public class Solve_Arg 
{
    Router routerPtr;
    Maze mazePtr;
    List_t pathVectorListPtr;
    int rblock_workload;

    public Solve_Arg(Router r,Maze m,List_t l, int workload)
    {
        routerPtr = r;
        mazePtr = m;
        pathVectorListPtr = l;
        rblock_workload = workload;
    }
}

