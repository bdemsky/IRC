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

void generateData(char fileName[],int numCmd,char** wordList,int numWord);

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

  wordList = readList(DIR_FILE,&numWord);

  for(i = 0;i< numFile;i++) {
    printf("Generating %s%d...\n",prefix,i);
    sprintf(fileName,"%s%d",prefix,i);
    generateData(fileName,numCmd,wordList,numWord);
  }

  freeList(wordList,numWord);


}

void generateData(char fileName[],int numCmd,char** wordList,int numWord)
{
  FILE* file = fopen(fileName,"w");
  char cmdString[STR_SIZE];
  char subCmdString[STR_SIZE];
  char* wordToken;
  int rand_index;
  int token;
  int i;
 
  // create initial directory on home
  sprintf(cmdString,"c /%s/%s/",HOME_DIR,fileName);
  fprintf(file,"%s\n",cmdString);
  numCmd--;

  while(numCmd > 0) {
    
    // creating directory
    wordToken = wordList[rand() % numWord];
    sprintf(subCmdString,"%s%s/",cmdString,wordToken);
    fprintf(file,"%s\n",subCmdString);
    numCmd--;

    if(numCmd == 0 )
      break;

    rand_index = (rand() % numCmd);
    rand_index /= 2;

    // creating files in the directory
    for(i = 0;i <rand_index && numCmd > 0;i++) {
      sprintf(subCmdString,"%s%s/%s%d",cmdString,wordToken,FILE_NAME,i);
      fprintf(file,"%s\n",subCmdString);
      numCmd--;
    }  
  }
  fclose(file);
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
