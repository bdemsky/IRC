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
void writeString(FILE* newFile,char* prefix,char** list,int size_list);

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

     // write header
     fprintf(newFile,"MessageID: %d\n",i+1);
     
     // write to account name
     writeString(newFile,"To: ",al,ac_num);
     fprintf(newFile,"\n");

     // write from account name
     writeString(newFile,"From: ",al,ac_num);
     fprintf(newFile,"\n");

     // write cc
     writeString(newFile,"Cc: ",al,ac_num);
     fprintf(newFile,"\n");

     // attachments
     writeString(newFile,"Attch: ",wl,word_num);
     fprintf(newFile,"\n");

     // write title
     writeString(newFile,"Title: ",wl,word_num);
     fprintf(newFile,"\n");

     // write Body
     bodyLength = rand() % 500 + 300;

     for(j=1;j<bodyLength;j++)
     {
       coin = rand() % 500;

       if(coin < 5) {           // if coin < 50, then write a URL
         if(coin <2)
           writeString(newFile,"",al,ac_num); // email
         else
           writeString(newFile," ",ul,url_num); // url
       }
       else {  // if not, write a word
         writeString(newFile," ",wl,word_num);
       }
       if((j % 10) == 0) 
        fprintf(newFile,"\n");
     }

     fprintf(newFile,"\n");
     fclose(newFile);
   }
}

void writeString(FILE* newFile,char* prefix,char** list,int size_list)
{
  int rand_index = rand() % size_list;
  char* str = list[rand_index];

  fprintf(newFile,"%s%s",prefix,str);
}






