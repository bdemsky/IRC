/*
   It generates random data files which contain a certain amount of command 
   for File System benchmark
 
  usage :
    ./gen <num_file> <num_command> <File prefix>
 */

#include<stdio.h>
#include<stdlib.h>
#include<string.h>
#include<time.h>

#define DIR_FILE "dirname"
#define FILE_NAME "file"
#define HOME_DIR "home"
#define STR_SIZE 256
#define CREAT_FILE "creates.txt"
#define FACTOR (0.05)

unsigned int num_lines;

void generateCrData(char fileName[],int numCmd,char** wordList,int numWord);
void generateRdData(char *filename, int numRdCmd);

/* to read word tokens*/
char** readList(char* fileName,int* num);
void freeList(char** list,int num);

int main(int argn,char** argv)
{
  char** wordList;
  int numWord;
  int numFile;
  int numCmd;
  char* prefix;
  char fileName[256];
  int i;
  int numCrCmd, numRdCmd;
  srand(0);

  if(argn < 4) {
    printf("Usage : ./gen <# of file to generate> <# of command> <File prefix>\n");
    printf(" Ex)    ./gen 8 800 data\n");
    printf("It generates 8 files(data0...data7) which contain 800 commands\n");
    exit(0);
  }
  else {
    sscanf(argv[1]," %d",&numFile);
    sscanf(argv[2]," %d",&numCmd);
    prefix = argv[3];
  }

  printf("# of file    : %d\n",numFile);
  printf("# of Command : %d\n",numCmd);
  numCrCmd = (int) (numCmd * FACTOR);
  numRdCmd = (int) (numCmd * (1.0 - FACTOR));
  printf("# of Read Command : %d\n",numRdCmd);
  printf("# of Create Command : %d\n", numCrCmd);

  wordList = readList(DIR_FILE,&numWord);

  /* Truncate the file */
  FILE *fp = fopen(CREAT_FILE, "w+");
  fclose(fp);

  for(i = 0;i< numFile;i++) {
    printf("Generating Creates %s%d...\n",prefix,i);
    sprintf(fileName,"%s%d",prefix,i);
    generateCrData(fileName,numCrCmd,wordList,numWord);
  }

  freeList(wordList,numWord);

  for (i = 0; i < numFile; i++) {
    printf("Generating Reads %s%d...\n",prefix,i);
    sprintf(fileName,"%s%d",prefix,i);
    generateRdData(fileName, numRdCmd);
  }


}

void generateRdData(char *filename, int numRdCmd) 
{
  FILE *fp = fopen(filename, "a+");
  FILE *fp_creates = fopen(CREAT_FILE, "r");
  char *rd_data[num_lines];
  int i;

  if (!fp || !fp_creates) {
    printf("error");
    return;
  }
  for (i = 0; i < num_lines; i++) {
    if ((rd_data[i] = (char *) calloc(sizeof(char), STR_SIZE)) < 0) {
      perror("");
      printf("Error at %d\n");
    }
  }
  for (i = 0; i < num_lines; i++) {
    fgets(rd_data[i], STR_SIZE, fp_creates);
  }
  for (i = 0; i < numRdCmd; i++) {
    int idx = rand() % num_lines;
    rd_data[idx][0] = 'r';
    fprintf(fp, "%s", rd_data[idx]);
  }
  fclose(fp);
  fclose(fp_creates);

  return;
}

void generateCrData(char fileName[],int numCmd,char** wordList,int numWord)
{
  FILE* file = fopen(fileName,"w");
  FILE* fp_creates = fopen(CREAT_FILE,"a+"); /* This is superset of all creates */
  char cmdString[STR_SIZE];
  char subCmdString[STR_SIZE];
  char* wordToken;
  int rand_index;
  int token;
  int i;
 
  // create initial directory on home
  sprintf(cmdString,"c /%s/%s/",HOME_DIR,fileName);
  fprintf(file,"%s\n",cmdString);
  fprintf(fp_creates,"%s\n",cmdString);
  num_lines++;
  numCmd--;

  while(numCmd > 0) {
    
    // creating directory
    wordToken = wordList[rand() % numWord];
    sprintf(subCmdString,"%s%s/",cmdString,wordToken);
    fprintf(file,"%s\n",subCmdString);
    fprintf(fp_creates,"%s\n",subCmdString);
    num_lines++;
    numCmd--;

    if(numCmd == 0 )
      break;

    rand_index = (rand() % numCmd);
    rand_index /= 2;

    // creating files in the directory
    for(i = 0;i <rand_index && numCmd > 0;i++) {
      sprintf(subCmdString,"%s%s/%s%d",cmdString,wordToken,FILE_NAME,i);
      fprintf(file,"%s\n",subCmdString);
      fprintf(fp_creates,"%s\n",subCmdString);
      num_lines++;
      numCmd--;
    }  
  }
  fclose(file);
  fclose(fp_creates);
}

char** readList(char* fileName,int* num)
{
  char ** list;
  int cnt = 0 ;
  char buffer[100];
  int size;
  
  FILE* fp = fopen(fileName,"r");

  while((fscanf(fp," %s",buffer)) != EOF) cnt++;  // to count the number of elements

  list = (char**)malloc(sizeof(char*) * cnt);

  rewind(fp);

  *num = 0;

  while((fscanf(fp," %s",buffer)) != EOF) // read actual list
  {
    size = strlen(buffer); // to get length of the word,url, or account
    list[*num] = (char*)malloc(sizeof(char) * size + 1);
    strcpy(list[*num],buffer);
    (*num)++;
  }

  fclose(fp);
  return list;  
}

void freeList(char** list,int num)
{
  int i;

  for(i=0;i<num;i++)
  {
    free(list[i]);
  }

  free(list);
}
