#include "gcqueue.h"

struct pointerblock *gchead=NULL;
int gcheadindex=0;
struct pointerblock *gctail=NULL;
int gctailindex=0;
struct pointerblock *gctail2=NULL;
int gctailindex2=0;
struct pointerblock *gcspare=NULL;

struct lobjpointerblock *gclobjhead=NULL;
int gclobjheadindex=0;
struct lobjpointerblock *gclobjtail=NULL;
int gclobjtailindex=0;
struct lobjpointerblock *gclobjtail2=NULL;
int gclobjtailindex2=0;
struct lobjpointerblock *gclobjspare=NULL;
