
public class Parameter {
  flag w;
  int a;
  int b;
  Foo f;
  Foo g;
  Parameter p;
  Parameter q;
  public Parameter() {}
}

public class Foo {
  Foo f;
  Foo g;
  Parameter p;
  Parameter q;
  public Foo() {}
  void noFlaggedAlias() {
    this.f.p = new Parameter();
  }
}

task Startup( StartupObject s{ initialstate } ) {
  Parameter p0 = new Parameter();
  taskexit( s{ !initialstate } );
}

task noAliasAlways( Parameter p0{ w }, Parameter p1{ !w } ) {
  p0.f = new Foo();
  p1.g = new Foo();
  taskexit( p0{ !w }, p1{ w } );
}

task noAliasWithStrong( Parameter p0{ w }, Parameter p1{ !w } ) {
  p0.f = new Foo();
  p1.g = p0.f;
  p1.g = new Foo();
  taskexit( p0{ !w }, p1{ w } );
}

task noAliasWithGlobal( Parameter p0{ w }, Parameter p1{ !w } ) {
  Foo f0 = new Foo();
  f0.f = new Foo();

  f0.p = p0;
  f0.f.p = p1;

  f0.noFlaggedAlias();

  taskexit( p0{ !w }, p1{ w } );
}

task noAliasWithGlobalAndStrong( Parameter p0{ w }, Parameter p1{ !w } ) {
  p0.f = new Foo();
  p0.f.f = new Foo();
  p0.f.f.p = new Parameter();

  p1.f = p0.f;
  p1.f.f.p = new Parameter();

  p0.f.f = null;
  p0.f = null;

  taskexit( p0{ !w }, p1{ w } );
}
