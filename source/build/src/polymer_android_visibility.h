// Android-only CPU visibility preparation for Polymer. No GL or mutable
// engine geometry is accessed by the worker; every job owns its coordinates.
#include <condition_variable>
#include <mutex>
#include <thread>
#include <vector>
#include <exception>
#include <pthread.h>

int32_t pr_workers = 2; // 0 serial, 1 single helper, 2 adaptive pair for large batches.

class AndroidVisibilityWorker
{
    std::mutex mutex;
    std::condition_variable wake, done;
    std::thread thread;
    void (*function)(void *) = nullptr;
    void *context = nullptr;
    bool pending = false, stopping = false, unavailable = false;
public:
    bool submit(void (*fn)(void *), void *ctx)
    {
        if (unavailable || std::thread::hardware_concurrency() < 2) return false;
        if (!thread.joinable())
        {
            try
            {
                thread = std::thread([this] {
                    pthread_setname_np(pthread_self(), "Polymer-cull");
                    std::unique_lock<std::mutex> lock(mutex);
                    for (;;)
                    {
                        wake.wait(lock, [this] { return pending || stopping; });
                        if (stopping) return;
                        auto fn = function;
                        auto ctx = context;
                        lock.unlock();
                        fn(ctx);
                        lock.lock();
                        pending = false;
                        done.notify_one();
                    }
                });
                LOG_F(INFO, "Polymer: CPU visibility worker started");
            }
            catch (std::exception const &error)
            {
                unavailable = true;
                LOG_F(WARNING, "Polymer: visibility worker unavailable: %s", error.what());
                return false;
            }
        }
        std::lock_guard<std::mutex> lock(mutex);
        if (pending) return false;
        function = fn;
        context = ctx;
        pending = true;
        wake.notify_one();
        return true;
    }
    void wait()
    {
        std::unique_lock<std::mutex> lock(mutex);
        done.wait(lock, [this] { return !pending; });
    }
    ~AndroidVisibilityWorker()
    {
        wait();
        {
            std::lock_guard<std::mutex> lock(mutex);
            stopping = true;
            wake.notify_one();
        }
        if (thread.joinable()) thread.join();
    }
};

struct AndroidVisibilityState
{
    struct Job { int16_t wall; int32_t x1, y1, x2, y2; };
    std::vector<int16_t> previous, requested;
    std::vector<Job> jobs;
    int8_t result[MAXWALLS];
    uint8_t visited[MAXWALLS];
    int32_t x, y;
    bool inUse = false;
    uint64_t computeTicks = 0;
    struct Batch { AndroidVisibilityState *state; size_t begin, end; uint64_t ticks=0; };
    Batch batches[2];
    static void compute(void *context)
    {
        auto &batch = *static_cast<Batch *>(context);
        auto &s = *batch.state;
        uint64_t const start = timerGetNanoTicks();
        for (size_t index=batch.begin; index<batch.end; ++index)
        {
            auto const &job=s.jobs[index];
            int32_t const a1 = gethiq16angle_threadsafe(job.x1 - s.x, job.y1 - s.y);
            int32_t const a2 = gethiq16angle_threadsafe(job.x2 - s.x, job.y2 - s.y);
            s.result[job.wall] = (((a2 + (F16(2048) - a1)) & 0x7FFFFFF) <= F16(1024));
        }
        batch.ticks = timerGetNanoTicks() - start;
    }
};

static AndroidVisibilityWorker androidVisibilityWorkers[2];
static AndroidVisibilityState androidVisibilityState;

class AndroidVisibilityFrame
{
    bool root, finished = false;
    bool background[2] = {false,false};
    unsigned batches=1;
    int mode;
    uint64_t start, waitTicks = 0;
    unsigned lookups = 0;
public:
    explicit AndroidVisibilityFrame(bool rootView) : root(rootView && !androidVisibilityState.inUse), mode(pr_workers), start(timerGetNanoTicks())
    {
        if (!root) return;
        auto &s = androidVisibilityState;
        s.inUse = true;
        Bmemset(s.result, -1, numwalls * sizeof(s.result[0]));
        Bmemset(s.visited, 0, numwalls * sizeof(s.visited[0]));
        s.x = globalposx;
        s.y = globalposy;
        s.computeTicks = 0;
        s.jobs.clear();
        s.requested.clear();
        // The previous pass supplies candidates only. Coordinates and viewpoint
        // come from THIS pass, and newly discovered walls use an exact fallback.
        for (int const id : s.previous)
        {
            if ((unsigned)id >= (unsigned)numwalls) continue;
            auto const &w1 = wall[id];
            auto const &w2 = wall[w1.point2];
            s.jobs.push_back({int16_t(id), w1.x, w1.y, w2.x, w2.y});
        }
        // Two helpers only pay off for larger immutable batches. Driver, audio
        // and render threads still need CPU time; never spawn a thread per core.
        batches=(mode >= 2 && s.jobs.size() >= 1024 && std::thread::hardware_concurrency() >= 4) ? 2 : 1;
        for (unsigned i=0; i<batches; ++i) {
            s.batches[i]={&s, s.jobs.size()*i/batches, s.jobs.size()*(i+1)/batches, 0};
            if (mode && s.jobs.size() >= 128)
                background[i]=androidVisibilityWorkers[i].submit(&AndroidVisibilityState::compute, &s.batches[i]);
            // Complete serial work now, overlapping sky submission only with
            // actual background tasks, rather than postponing all CPU work.
            if (!background[i]) AndroidVisibilityState::compute(&s.batches[i]);
        }
    }
    void finish()
    {
        if (!root || finished) return;
        uint64_t const before = timerGetNanoTicks();
        for(unsigned i=0; i<batches; ++i) {
            if(background[i]) androidVisibilityWorkers[i].wait();
            androidVisibilityState.computeTicks += androidVisibilityState.batches[i].ticks;
        }
        waitTicks = timerGetNanoTicks() - before;
        finished = true;
    }
    int visible(int id)
    {
        if (!root) return wallvisible(globalposx, globalposy, id);
        auto &s = androidVisibilityState;
        ++lookups;
        if (!s.visited[id])
        {
            s.visited[id] = 1;
            s.requested.push_back(id);
        }
        if (s.result[id] < 0) s.result[id] = wallvisible(s.x, s.y, id);
        return s.result[id];
    }
    ~AndroidVisibilityFrame()
    {
        if (!root) return;
        finish();
        auto &s = androidVisibilityState;
        struct Stats { unsigned frames=0; uint64_t pass=0, compute=0, wait=0, jobs=0, walls=0, calls=0, workerFrames=0, pairFrames=0; int mode=-1; };
        static Stats stats;
        if (stats.mode != mode) { stats = {}; stats.mode = mode; }
        ++stats.frames;
        stats.pass += timerGetNanoTicks() - start;
        stats.compute += s.computeTicks;
        stats.wait += waitTicks;
        stats.jobs += s.jobs.size();
        stats.walls += s.requested.size();
        stats.calls += lookups;
        stats.workerFrames += background[0];
        stats.pairFrames += background[1];
        if (stats.frames == 300)
        {
            double const us = 1000000.0 / double(timerGetNanoTickRate()) / stats.frames;
            LOG_F(INFO, "Polymer CPU visibility: mode=%d worker=%u/300 pair=%u/300 pass=%.1fus batch=%.1fus wait=%.1fus jobs=%.1f walls=%.1f calls=%.1f",
                  mode, stats.workerFrames, stats.pairFrames, stats.pass*us, stats.compute*us, stats.wait*us,
                  double(stats.jobs)/stats.frames, double(stats.walls)/stats.frames, double(stats.calls)/stats.frames);
            stats = {}; stats.mode = mode;
        }
        s.previous.swap(s.requested);
        s.inUse = false;
    }
};
