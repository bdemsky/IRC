#include<stdio.h>
#include<stdlib.h>
#include<string.h>

#define WORD_FILE "wordList"
#define URL_FILE  "URLList"
#define ACCOUNT_FILE "accountList"
#define FILE_NAME "email"
#define NUM_EMAIL 100

char** readList(char* fileName,int* num);
void generateEmails(int,char**,int,char**,int,char**,int,char*);
void freeList(char**,int);

int main()
{
  int num_email = NUM_EMAIL;                  // how many emails do you need?
  char** wordList;
  char** urlList;
  char** accountList;
  int word_num;
  int url_num;
  int account_num;
  
  wordList = readList(WORD_FILE,&word_num);
  urlList  = readList(URL_FILE,&url_num);
  accountList = readList(ACCOUNT_FILE,&account_num);

  generateEmails(NUM_EMAIL,wordList,word_num,urlList,url_num,accountList,account_num,FILE_NAME);
 
  freeList(wordList,word_num);
  freeList(urlList,url_num);
  freeList(accountList,account_num);

  return 0;
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

  return list;  
}

void generateEmails(int num_email,char** wl,int word_num,char** ul,int url_num,char** al,int ac_num,char* fileName)
{
   int i;
   int j;
   FILE* newFile;
   char fileNameBuffer[100];
   char* ptr;
   int rand_index;

   int bodyLength;
   int coin;
   srand(1);

   for(i=0; i < num_email;i++) {


     sprintf(fileNameBuffer,"%s%d",fileName,i+1);
     newFile = fopen(fileNameBuffer,"w");

     // write account name
     rand_index = rand() % ac_num;
     ptr = al[rand_index];    // get random account name
     fprintf(newFile,"%s\n",ptr);

     // write header
     fprintf(newFile,"Title%d\n",i+1);

     // write Body
     bodyLength = rand() % 500 + 300;

     for(j=0;j<bodyLength;j++)
     {
       coin = rand() % 500;

       if(coin < 5) {           // if coin < 50, then write a URL
         rand_index = rand() % url_num;
         ptr = ul[rand_index];
       }
       else {  // if not, write a word
         rand_index = rand() % word_num;
         ptr = wl[rand_index];
       }

       fprintf(newFile,"%s ",ptr);

       if((j % 10) == 0) 
        fprintf(newFile,"\n");
     }

     fprintf(newFile,"\n");
     fclose(newFile);
   }
}








