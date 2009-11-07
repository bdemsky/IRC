/**
 * This class is a container for all data contained in an Email Message.
 **/
public class Mail {

	String header; // the full header
	//String sentOn; // time the message was sent
	//String receivedOn; // time when the message arrived
	String from; // the "from" field
	String to; // the "to" field
	String cc;  
	String subject;
	String body;
    String noURLBody;
	String sourceCode;
    String spam;
	boolean hasAttachement;
	String encoding; //rich text, plain, html

	String messageID; // cached message ID for reuse (takes a lot of memory and is used all over the place)
                      //same as hashcode of a class
    boolean isSpam;

  public Mail() {
      messageID=null;
  }

  public Mail(String fileName)  // read a mail from file
  {
    //System.out.println("fileName= " + fileName);

    FileInputStream fileinput = new FileInputStream(fileName);
    String line;
    boolean chk = false;

    while((line = fileinput.readLine()) != null)
    {
      chk = true;

      Vector splittedLine = line.split();
      if(((String)(splittedLine.elementAt(0))).equals("Spam:"))
      {
        spam = (String)splittedLine.elementAt(1);
      }
      else if(((String)(splittedLine.elementAt(0))).equals("Header:"))  // message id
      {
        header = (String)splittedLine.elementAt(1);
      }
      else if(((String)(splittedLine.elementAt(0))).equals("To:")) // receiver
      {
        to = (String)splittedLine.elementAt(1);
      }
      else if(((String)(splittedLine.elementAt(0))).equals("From:")) // sender
      {
        from = (String)splittedLine.elementAt(1);
      }
      else if(((String)(splittedLine.elementAt(0))).equals("Cc:")) // cc
      {
        cc = (String)splittedLine.elementAt(1);
      }
      else if(((String)(splittedLine.elementAt(0))).equals("Title:")) // Subject
      {
        subject = (String)splittedLine.elementAt(1);
        break;
      }
    } // parsed messageID, To, from, cc, Title

    if(!chk)
      System.out.println("no line read");


    body = new String();

    while((line = fileinput.readLine()) != null)
    {
      body += line;
    }

    fileinput.close();
  }

	// -------------------------------------------------------

	public void setHeader(String header) {
		this.header = header;
	}

	public String getHeader() {
		return header;
	}

   
    /*
	public void setSentOn(String sentOn) {
		this.sentOn = sentOn;
	}

	public String getSentOn() {
		return sentOn;
	}

	public Date getSentOnAsDate() {
		String sentOn = getSentOn();
		return parseDate(sentOn);
	}

	public void setReceivedOn(String receivedOn) {
		this.receivedOn = receivedOn;
	}

	public String getReceivedOn() {
		return receivedOn;
	}

	public Date getReceivedOnAsDate() {
		String receivedOn = getReceivedOn();
		return parseDate(receivedOn);
	}
    */
    

	/**
	 * Parses a given Date-String in into a real Date-Object
	 * 
	 * @param stringDate the string in format dd.mm.yyyy hh:mm
	 * @return a Date containing the info of the string or the actual date and time if something fails.
	 */
    /*
	public Date parseDate(String stringDate) {
		// date is in this format: dd.mm.yyyy hh:mm
		if (stringDate == null || "N/A".equals(stringDate)) {
			return new Date();
		}
		try {
			synchronized (MAIL_TIME_FORMAT) {
				return MAIL_TIME_FORMAT.parse(stringDate);
			}
		} catch (Throwable e) {
			return new Date();
		}
	}
    */

	public void setFrom(String from) {
		this.from = from;
	}

	public String getFrom() {
		return from;
	}

	public void setTo(String to) {
		this.to = to;
	}

	public String getTo() {
		return to;
	}

	public void setCc(String cc) {
		this.cc = cc;
	}

	public String getCc() {
		return cc;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getSubject() {
		return subject;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getBody() {
		return body;
	}

	public void setSourceCode(String sourceCode) {
		this.sourceCode = sourceCode;
	}

	public String getSourceCode() {
		return sourceCode;
	}

	public void setHasAttachement(boolean hasAttachement) {
		this.hasAttachement = hasAttachement;
	}

	public boolean getHasAttachement() {
		return hasAttachement;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public String getEncoding() {
		return encoding;
	}

	public boolean isTextEncoding() {
		return getEncoding().toLowerCase().indexOf("plain") >= 0;
	}

	public boolean isHTMLEncoding() {
		return getEncoding().toLowerCase().indexOf("html") >= 0;
	}

    /*
	public String toString() {
		return getBody() + "," + getCc() + "," + getEncoding() + "," + getFrom() + "," + getHasAttachement() + "," + getHeader() + "," + getReceivedOn() + "," + getSentOn() + "," + getSourceCode() + "," + getSubject() + "," + getTo();
	}
    */

	public String toString() {
		return getBody() + "," + getCc() + "," + getEncoding() + "," + getFrom() + "," + getHasAttachement() + "," + getHeader() + "," + getSourceCode() + "," + getSubject() + "," + getTo();
    }

    /*
	public String getID() {
		if (messageID == null) { // no cached version
			// Take the message-ID header as ID (if present)
			String[] messageIDs = getHeaderField("Message-ID");
			if ((messageIDs != null) && (messageIDs.length > 0)) {
				messageID = messageIDs[0];
			} else { // otherwise, hash header and body as ID
				return String.valueOf(getHeader().hashCode() + getBody().hashCode());
			}
		}

		return messageID;
	}
    */

	public String[] getHeaderField(String fieldName) {

	}

    public String extractEMailAddress() {

	}

    /*
	public boolean equals(Object o) {
		if (o instanceof Mail) {
			Mail mail = (Mail)o;
			return this.getID().equals(mail.getID());
		}

		return false;
	}
    */

  public Vector getCommonPart()
  {
    Vector returnStrings = new Vector();

    // add header, sender, and title
    returnStrings.addElement(header);
    returnStrings.addElement(from);
    returnStrings.addElement(subject);

    return returnStrings;
  }

  public String getBodyString()
  {
    return body;
  }

  /* TODO add this to process entire email
  public Vector returnEmail() {
    Vector myemail = new Vector();

    myemail.addElement(getCommonPart());
    myemail.addElement(getURLs());
    myemail.addElement(getSplittedBody());
    return myemail;
  }
  */

  public Vector getURLs()
  {
    Vector returnStrings = new Vector();
    Vector splittedBody = body.split();

    // add URL and email in the body
    for(int i=0; i<splittedBody.size(); i++) 
    {
      String segment = (String)splittedBody.elementAt(i);
      if(segment.startsWith("http://"))  // URL
      {
        returnStrings.addElement(segment);
      }
      else if(isEmailAccount(segment)) // email
      {
        returnStrings.addElement(segment);
      }
    }

    return returnStrings;
  }

  // check if it is email account string
  private boolean isEmailAccount(String str)
  {
    if(str.contains("@") && str.contains("."))
      return true;
    else
      return false;
  }

  public void setNoURLBody()
  {
    noURLBody = new String();
    Vector splittedBody = body.split();

    for(int i=0; i< splittedBody.size();i ++)
    {
      String segment = (String)splittedBody.elementAt(i);
      
      if(!(segment.startsWith("http://") || isEmailAccount(segment)))
        noURLBody += segment;
    }
  }

  // setNoURLBody method has to be called before this method
  // parameter : bytesize to split.
  public Vector getSplittedBody(int size)
  {
    setNoURLBody();
    Vector returnStrings = new Vector();

    char[] charArray = noURLBody.toCharArray();

    String tmpStr = new String();
    tmpStr += charArray[0];

    for(int i=1; i< noURLBody.length(); i++)
    {
      if((i % size) == 0) {
        returnStrings.addElement(tmpStr);
        tmpStr = new String();
      }
      else {
        tmpStr += charArray[i];
      }
    }

    returnStrings.addElement(tmpStr);

    return returnStrings;
  }


  public void setIsSpam(boolean spam) {
    isSpam = spam;
  }

  public boolean getIsSpam() {
    if(spam.equals("yes"))
      return true;
    return false;
  }

  /**
   *  Returns result to the Spam filter
   **/
  public Vector checkMail(int userid) {
    //Preprocess emails
    //Vector partsOfMailStrings = mail.createMailStringsWithURL();
    Vector partsOfMailStrings = getCommonPart();
    //partsOfMailStrings.addElement(getBodyString());

    //Compute signatures
    SignatureComputer sigComp = new SignatureComputer();
    Vector signatures = sigComp.computeSigs(partsOfMailStrings);//vector of strings

    return signatures;
  }

  /* For tests only */
  /*
  public static void main(String[] args)
  {
    Mail mail = new Mail("./emails/email1");

    String[] a = mail.createMailStrings();

    for(String b : a)
    {
      System.out.println(b);
    }
  }
  */
}
