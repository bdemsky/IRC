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
void writeString(FILE* newFile,char* prefix,char** list,int size_list,int* counter);

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

  fclose(fp);
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
   int accountCounter = 0;
   int wordCounter = 0;
   int urlCounter = 0;
   srand(1);

   for(i=0; i < num_email;i++) {
     sprintf(fileNameBuffer,"%s%d",fileName,i+1);
     newFile = fopen(fileNameBuffer,"w");

     // write spam or no spam 
     // 60% of email is spam and rest is ham
     char yes[] = "yes";
     char no[] =  "no";
     int tmprandindex = rand() % 100;
     if(tmprandindex<60)
       fprintf(newFile,"Spam: %s\n",yes);
     else 
       fprintf(newFile,"Spam: %s\n",no);

     // write header
     fprintf(newFile,"Header: %d\n",i+1);
     
     // write to account name
     writeString(newFile,"To: ",al,ac_num,&accountCounter);
     fprintf(newFile,"\n");

     // write from account name
     writeString(newFile,"From: ",al,ac_num,&accountCounter);
     fprintf(newFile,"\n");

     // write cc
     writeString(newFile,"Cc: ",al,ac_num,&accountCounter);
     fprintf(newFile,"\n");

     // attachments
     writeString(newFile,"Attch: ",wl,word_num,&wordCounter);
     fprintf(newFile,"\n");

     // write title
     writeString(newFile,"Subject: ",wl,word_num,&wordCounter);
     fprintf(newFile,"\n");

     // write Body
     //TODO change this to make length of email small
     bodyLength = rand() % 200 + 150;

     for(j=1;j<bodyLength;j++)
     {
       coin = rand() % 500;

       if(coin < 5) {           // if coin < 50, then write a URL
         if(coin <2)
           writeString(newFile,"",al,ac_num,&accountCounter); // email
         else
           writeString(newFile," ",ul,url_num,&urlCounter); // url
       }
       else {  // if not, write a word
         writeString(newFile," ",wl,word_num,&wordCounter);
       }
       if((j % 10) == 0) 
        fprintf(newFile,"\n");
     }

     fprintf(newFile,"\n");
     fclose(newFile);
   }
}

void writeString(FILE* newFile,char* prefix,char** list,int size_list,int* counter)
{
  char* str = list[*counter];

  fprintf(newFile,"%s%s",prefix,str);

  (*counter)++;

  if(*counter == size_list)
    *counter = 0;
}






