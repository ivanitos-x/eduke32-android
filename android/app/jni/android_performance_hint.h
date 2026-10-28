// Optional ADPF integration. Resolve the API at runtime to retain Android 7+
// compatibility. The render/game thread owns this object and all sessions.
#include <cstdint>
#include <cstddef>
#include <dlfcn.h>
#include <unistd.h>
#include <android/log.h>
#include <sched.h>
#include <cstdio>
#include <atomic>

int32_t pr_adpf=1;
int32_t pr_bigcores=0;
static std::atomic<int> androidBigPreference{-1};
class AndroidRenderCorePolicy {
    cpu_set_t original{},big{};
    bool initialized=false,available=false,applied=false,failed=false;
    void initialize() {
        initialized=true;
        if(sched_getaffinity(0,sizeof(original),&original)!=0) return;
        int capacities[CPU_SETSIZE]={};int highest=0,lowest=0x7fffffff,all=0,selected=0;
        for(int cpu=0;cpu<CPU_SETSIZE;++cpu) if(CPU_ISSET(cpu,&original)) {
            char name[128];snprintf(name,sizeof(name),"/sys/devices/system/cpu/cpu%d/cpu_capacity",cpu);
            FILE *fp=fopen(name,"r");if(!fp) continue;
            int cap=0;int result=fscanf(fp,"%d",&cap);fclose(fp);
            if(result!=1 || cap<=0) continue;
            capacities[cpu]=cap;if(cap>highest)highest=cap;if(cap<lowest)lowest=cap;++all;
        }
        CPU_ZERO(&big);
        if(highest<=0 || highest*4<=lowest*5) return; // Homogeneous or unknown topology.
        for(int cpu=0;cpu<CPU_SETSIZE;++cpu) if(capacities[cpu]*10>=highest*9) {CPU_SET(cpu,&big);++selected;}
        available=selected>0 && selected<all;
        __android_log_print(ANDROID_LOG_INFO,"EDuke32","CPU topology: %d allowed CPUs, %d high-capacity CPUs; capacity %d..%d",all,selected,lowest,highest);
    }
public:
    void apply() {
        int pending=androidBigPreference.exchange(-1,std::memory_order_relaxed);
        if(pending>=0) pr_bigcores=pending;
        if(!initialized) initialize();
        bool const want=pr_bigcores && available && !failed;
        if(want==applied) return;
        if(sched_setaffinity(0,sizeof(original),want?&big:&original)!=0) {
            failed=true;
            __android_log_print(ANDROID_LOG_INFO,"EDuke32","Render CPU preference unavailable; retaining Android scheduling");return;
        }
        applied=want;
        __android_log_print(ANDROID_LOG_INFO,"EDuke32","Render CPU preference: %s",applied?"high-capacity cores":"Android default");
    }
    void restore() { if(applied) {sched_setaffinity(0,sizeof(original),&original);applied=false;} }
    ~AndroidRenderCorePolicy() { restore(); }
};
static AndroidRenderCorePolicy androidRenderCorePolicy;

class AndroidRenderHints {
    using GetManager=void *(*)();
    using Preferred=int64_t (*)(void *);
    using Create=void *(*)(void *,int32_t const *,size_t,int64_t);
    using Update=int (*)(void *,int64_t);
    using Report=int (*)(void *,int64_t);
    using Close=void (*)(void *);
    void *library=nullptr,*manager=nullptr,*session=nullptr;
    Create create=nullptr;
    Update update=nullptr;
    Report report=nullptr;
    Close close=nullptr;
    int64_t target=0;
    bool attempted=false,failed=false,logged=false;
    void initialize() {
        attempted=true;
        library=dlopen("libandroid.so",RTLD_NOW|RTLD_LOCAL);
        if(!library) return;
        auto get=(GetManager)dlsym(library,"APerformanceHint_getManager");
        auto preferred=(Preferred)dlsym(library,"APerformanceHint_getPreferredUpdateRateNanos");
        create=(Create)dlsym(library,"APerformanceHint_createSession");
        update=(Update)dlsym(library,"APerformanceHint_updateTargetWorkDuration");
        report=(Report)dlsym(library,"APerformanceHint_reportActualWorkDuration");
        close=(Close)dlsym(library,"APerformanceHint_closeSession");
        if(get && preferred && create && update && report && close) {
            void *candidate=get();
            if(candidate && preferred(candidate)>0) manager=candidate;
        }
        if(!manager) __android_log_print(ANDROID_LOG_INFO,"EDuke32","ADPF unavailable; using Android default scheduling");
    }
public:
    void stop() {
        if(session) close(session);
        session=nullptr;target=0;
    }
    void submit(uint64_t work,uint64_t budget) {
        if(!pr_adpf) { stop();return; }
        if(!attempted) initialize();
        if(!manager || failed || work==0 || work>500000000ULL) return;
        // Clamp corrupt/startup targets; respect the engine's actual frame cap.
        int64_t const next=(int64_t)(budget<4000000?4000000:budget>100000000?100000000:budget);
        if(!session) {
            int32_t const tid=gettid();
            session=create(manager,&tid,1,next);
            if(!session) {
                failed=true;
                __android_log_print(ANDROID_LOG_INFO,"EDuke32","ADPF session declined; using Android default scheduling");
                return;
            }
            target=next;
            if(!logged) {
                logged=true;
                __android_log_print(ANDROID_LOG_INFO,"EDuke32","ADPF render session active: tid=%d target=%.2fms; scheduler chooses CPU cores",tid,next/1000000.0);
            }
        }
        if(next!=target) {
            if(update(session,next)!=0) { stop();failed=true;return; }
            target=next;
        }
        if(report(session,(int64_t)work)!=0) { stop();failed=true; }
    }
    ~AndroidRenderHints() { stop(); }
};
static AndroidRenderHints androidRenderHints;
extern "C" void AndroidReportRenderWork(uint64_t workNanos,uint64_t targetNanos) {
    androidRenderCorePolicy.apply();
    androidRenderHints.submit(workNanos,targetNanos);
}
extern "C" void AndroidClosePerformanceSession(void) { androidRenderHints.stop(); androidRenderCorePolicy.restore(); }
