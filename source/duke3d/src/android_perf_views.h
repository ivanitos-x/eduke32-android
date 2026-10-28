// Debug console bookmarks live in the Android settings directory, not game data.
#include <vector>
#include <cstdio>
#include <cstring>

struct AndroidPerfView {
    char name[32];
    int volume,level,x,y,z,angle,horizon,horizonOffset,sector;
};
static bool AndroidPerfViewName(char const *name) {
    size_t n=strlen(name);if(!n || n>=32)return false;
    for(size_t i=0;i<n;++i)if(!((name[i]>='a'&&name[i]<='z')||(name[i]>='A'&&name[i]<='Z')
        ||(name[i]>='0'&&name[i]<='9')||name[i]=='_'||name[i]=='-'))return false;
    return true;
}
static std::vector<AndroidPerfView> AndroidReadPerfViews() {
    std::vector<AndroidPerfView> result;
    FILE *fp=fopen("android-perf-views.txt","r");if(!fp)return result;
    char line[256];
    while(result.size()<64 && fgets(line,sizeof(line),fp)) {
        AndroidPerfView v{};
        if(sscanf(line,"%31s %d %d %d %d %d %d %d %d %d",v.name,&v.volume,&v.level,
            &v.x,&v.y,&v.z,&v.angle,&v.horizon,&v.horizonOffset,&v.sector)==10 && AndroidPerfViewName(v.name))result.push_back(v);
    }
    fclose(fp);return result;
}
static int AndroidPerfViewCommand(osdcmdptr_t parm) {
    if(parm->numparms!=1 || !AndroidPerfViewName(parm->parms[0]))return OSDCMD_SHOWHELP;
    auto p=g_player[myconnectindex].ps;
    if(g_netClient || g_netServer || ud.multimode>1 || ud.recstat || !(p->gm&MODE_GAME) || p->dead_flag) {
        LOG_F(WARNING,"Performance views require a live single-player game without demo recording");return OSDCMD_OK;
    }
    auto views=AndroidReadPerfViews();
    bool const mark=!Bstrcasecmp(parm->name,"perfmark");
    auto found=views.end();for(auto it=views.begin();it!=views.end();++it)if(!strcmp(it->name,parm->parms[0])){found=it;break;}
    if(mark) {
        AndroidPerfView v{};Bstrncpyz(v.name,parm->parms[0],sizeof(v.name));
        v.volume=ud.volume_number;v.level=ud.level_number;
        v.x=p->pos.x;v.y=p->pos.y;v.z=p->pos.z;v.angle=p->q16ang;
        v.horizon=p->q16horiz;v.horizonOffset=p->q16horizoff;v.sector=p->cursectnum;
        if(found!=views.end())*found=v;else if(views.size()<64)views.push_back(v);else return OSDCMD_OK;
        FILE *fp=fopen("android-perf-views.txt.tmp","w");if(!fp){LOG_F(ERROR,"Cannot save performance view");return OSDCMD_OK;}
        bool ok=true;
        for(auto const &entry:views)if(fprintf(fp,"%s %d %d %d %d %d %d %d %d %d\n",entry.name,entry.volume,entry.level,
            entry.x,entry.y,entry.z,entry.angle,entry.horizon,entry.horizonOffset,entry.sector)<0)ok=false;
        if(fclose(fp)!=0)ok=false;
        if(!ok || rename("android-perf-views.txt.tmp","android-perf-views.txt")!=0){LOG_F(ERROR,"Cannot commit performance view");return OSDCMD_OK;}
        LOG_F(INFO,"Perf view '%s': E%dL%d xyz=%d,%d,%d angle=%d horizon=%d sector=%d",v.name,v.volume+1,v.level+1,v.x,v.y,v.z,v.angle,v.horizon,v.sector);
    } else {
        if(found==views.end()){LOG_F(WARNING,"Unknown performance view '%s'",parm->parms[0]);return OSDCMD_OK;}
        auto const &v=*found;
        if(v.volume!=ud.volume_number || v.level!=ud.level_number){LOG_F(WARNING,"Load E%dL%d before perfwarp %s",v.volume+1,v.level+1,v.name);return OSDCMD_OK;}
        if((unsigned)v.sector>=(unsigned)numsectors){LOG_F(WARNING,"Performance view sector is invalid");return OSDCMD_OK;}
        int16_t sector=v.sector;updatesectorz(v.x,v.y,v.z,&sector);
        if(sector<0){LOG_F(WARNING,"Performance view is outside this map");return OSDCMD_OK;}
        p->pos={v.x,v.y,v.z};p->opos=p->npos=p->pos;p->bobpos={v.x,v.y};
        p->vel={0,0,0};p->fric={0,0};p->cursectnum=sector;
        p->q16ang=p->oq16ang=v.angle;p->q16horiz=p->oq16horiz=v.horizon;
        p->q16horizoff=p->oq16horizoff=v.horizonOffset;
        vec3_t feet=p->pos;feet.z+=p->spritezoffset;setsprite(p->i,&feet);
        sprite[p->i].ang=fix16_to_int(p->q16ang);actor[p->i].bpos=feet;
        AndroidResetButtons();
        LOG_F(INFO,"Restored performance view '%s'",v.name);
    }
    return OSDCMD_OK;
}
