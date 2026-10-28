#include <cmath>
// Additional additive light passes cannot contribute outside the light range.
// Project a conservative enclosing cube once per light/view. Workers do not
// touch this state: GL submission and restoration remain on the render thread.
int32_t pr_lightscissor = 1;

class AndroidLightScissorView;
static AndroidLightScissorView *androidLightScissorView;

class AndroidLightScissorView {
    struct Rect { GLint x=0,y=0,w=0,h=0; bool ready=false; };
    Rect rectangles[PR_MAXLIGHTS];
    GLfloat model[16], projection[16];
    GLint viewport[4];
    AndroidLightScissorView *previous;
public:
    GLboolean originalEnabled;
    GLint originalBox[4];
    unsigned restricted=0, passes=0;
    double area=0;
    AndroidLightScissorView(bool root, GLfloat const *m, GLfloat const *p) : previous(androidLightScissorView) {
        androidLightScissorView=nullptr;
        if(!root || !pr_lightscissor) return;
        Bmemcpy(model,m,sizeof(model)); Bmemcpy(projection,p,sizeof(projection));
        glGetIntegerv(GL_VIEWPORT,viewport);
        originalEnabled=glIsEnabled(GL_SCISSOR_TEST);
        glGetIntegerv(GL_SCISSOR_BOX,originalBox);
        androidLightScissorView=this;
    }
    bool bounds(int id, GLint *box) {
        if((unsigned)id>=PR_MAXLIGHTS || !prlights[id].flags.active || prlights[id].range<=0) return false;
        auto &r=rectangles[id];
        ++passes;
        if(!r.ready) {
            r.ready=true;
            // Default to the full viewport if the box crosses the eye plane,
            // or the camera is inside the enclosing cube. Never clip a near light.
            r.x=viewport[0]; r.y=viewport[1]; r.w=viewport[2]; r.h=viewport[3];
            float loX=1.f,loY=1.f,hiX=-1.f,hiY=-1.f;
            auto const &light=prlights[id];
            float const center[3]={(float)light.y,-light.z/16.f,-(float)light.x};
            for(unsigned corner=0;corner<8;++corner) {
                float point[4]={center[0]+((corner&1)?light.range:-light.range),
                    center[1]+((corner&2)?light.range:-light.range),
                    center[2]+((corner&4)?light.range:-light.range),1.f};
                float eye[4]={},clip[4]={};
                for(int row=0;row<4;++row)
                    for(int col=0;col<4;++col) eye[row]+=model[col*4+row]*point[col];
                for(int row=0;row<4;++row)
                    for(int col=0;col<4;++col) clip[row]+=projection[col*4+row]*eye[col];
                if(!(clip[3]>0.000001f)) return false;
                float const x=clip[0]/clip[3],y=clip[1]/clip[3];
                if(!std::isfinite(x) || !std::isfinite(y)) return false;
                loX=min(loX,x);loY=min(loY,y);hiX=max(hiX,x);hiY=max(hiY,y);
            }
            // Clamp in NDC before conversion to avoid extreme float-to-int casts.
            loX=max(-1.f,min(1.f,loX));loY=max(-1.f,min(1.f,loY));
            hiX=max(-1.f,min(1.f,hiX));hiY=max(-1.f,min(1.f,hiY));
            GLint x0=max(viewport[0],viewport[0]+(GLint)floorf((loX*.5f+.5f)*viewport[2])-2);
            GLint y0=max(viewport[1],viewport[1]+(GLint)floorf((loY*.5f+.5f)*viewport[3])-2);
            GLint x1=min(viewport[0]+viewport[2],viewport[0]+(GLint)ceilf((hiX*.5f+.5f)*viewport[2])+2);
            GLint y1=min(viewport[1]+viewport[3],viewport[1]+(GLint)ceilf((hiY*.5f+.5f)*viewport[3])+2);
            r.x=x0;r.y=y0;r.w=max(0,x1-x0);r.h=max(0,y1-y0);
        }
        if(r.w>=viewport[2] && r.h>=viewport[3]) return false;
        GLint x0=r.x,y0=r.y,x1=r.x+r.w,y1=r.y+r.h;
        if(originalEnabled) {
            x0=max(x0,originalBox[0]);y0=max(y0,originalBox[1]);
            x1=min(x1,originalBox[0]+originalBox[2]);y1=min(y1,originalBox[1]+originalBox[3]);
        }
        box[0]=x0;box[1]=y0;box[2]=max(0,x1-x0);box[3]=max(0,y1-y0);
        ++restricted;
        area+=double(box[2])*box[3]/max(1.0,double(viewport[2])*viewport[3]);
        return true;
    }
    ~AndroidLightScissorView() {
        if(androidLightScissorView==this) {
            static unsigned frames=0,scissored=0,total=0;
            static double fraction=0;
            ++frames;scissored+=restricted;total+=passes;fraction+=area;
            if(frames==300) {
                LOG_F(INFO,"Polymer light bounds: extra passes=%u restricted=%u average rectangle=%.1f%% of viewport",
                    total,scissored,scissored?100.0*fraction/scissored:100.0);
                frames=scissored=total=0;fraction=0;
            }
        }
        androidLightScissorView=previous;
    }
};

class AndroidLightScissorPass {
    AndroidLightScissorView *view=nullptr;
public:
    explicit AndroidLightScissorPass(int light) {
        GLint box[4];
        if(light>=0 && androidLightScissorView && androidLightScissorView->bounds(light,box)) {
            view=androidLightScissorView;
            buildgl_setEnabled(GL_SCISSOR_TEST);
            glScissor(box[0],box[1],box[2],box[3]);
        }
    }
    ~AndroidLightScissorPass() {
        if(!view) return;
        glScissor(view->originalBox[0],view->originalBox[1],view->originalBox[2],view->originalBox[3]);
        if(!view->originalEnabled) buildgl_setDisabled(GL_SCISSOR_TEST);
    }
};
