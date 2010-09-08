import java.io.*;
import java.util.*;

public class WorkerData {
  BufferedInputStream dataStream;
  int                 numDataWords;

  Set<Event>          rootEvents;
  Vector<Event>       eventStack;
}
