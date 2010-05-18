public class Directory {
  GlobalString name;
  DistributedHashMap table;
  DistributedLinkedList files;

  public Directory(GlobalString name) {
    this.name=name;
    this.table= new DistributedHashMap(500, 0.75f);
    this.files= new DistributedLinkedList();
  }

  public DFile getFile(GlobalString name) {
    return (DFile) table.get(name);
  }

  public DFile createFile(GlobalString name) {
    DFile file= new DFile();
    if (!table.containsKey(name)) {
      files.add(name);
    }
    table.put(name, file);
    return file;
  }

  public Directory getDirectory(GlobalString name) {
    return (Directory) table.get(name);
  }

  public Directory makeDirectory(GlobalString name) {
    if (!table.containsKey(name)) {
      Directory d= new Directory(name);
      files.add(name);
      table.put(name, d);
      return d;
    } else
      return (Directory) table.get(name);
  }

  public void init() {
    Random r=new Random();
    for(int count=0; count<100; count++) {
      GlobalString filename= new GlobalString(String.valueOf(r.nextInt(200)));
      createFile(filename);
    }
  }
}
