package Analysis.OoOJava;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

// the reason for this class is to allow a VariableSourceToken
// to be null in some circumstances

public class VSTWrapper {
  public VariableSourceToken vst;

  public VSTWrapper() {
    vst = null;
  }
}
