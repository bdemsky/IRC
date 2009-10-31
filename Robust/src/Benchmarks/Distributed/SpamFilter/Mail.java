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
	String sourceCode;
	boolean hasAttachement;
	//String encoding; //rich text, plain, html

	String messageID; // cached message ID for reuse (takes a lot of memory and is used all over the place)
                      //same as hashcode of a class

  public Mail() {
      messageID=null;
  }

  public Mail(String fileName)  // read a mail from file
  {
    FileInputStream fileinput = new FileInputStream(fileName);
    String line;
    
    while((line = fileinput.readLine()) != null)
    {
      String[] splittedLine = line.split();
      if(splittedLine[0].equals("MessageID:"))  // message id
      {
        header = splittedLine[1];
      }
      else if(splittedLine[0].equals("To:")) // receiver
      {
        to = splittedLine[1];
      }
      else if(splittedLine[0].equals("From:")) // sender
      {
        from = splittedLine[1];
      }
      else if(splittedLine[0].equals("Cc:")) // cc
      {
        cc = splittedLine[1];
      }
      else if(splittedLine[0].equals("Title:")) // Subject
      {
        subject = splittedLine[1];
        break;
      }
    } // parsed messageID, To, from, cc, Title

    body = new String();

    while((line = fileinput.readLine()) != null)
    {
      body += line;
    }
  }

	// -------------------------------------------------------

	public void setHeader(String header) {
		this.header = header;
	}

	public String getHeader() {
		return header;
	}

	public void setSentOn(String sentOn) {
		this.sentOn = sentOn;
	}

	public String getSentOn() {
		return sentOn;
	}

    /*
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

	// TODO: String? Is this a boolean, a number, or can be both?
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

	public String toString() {
		return getBody() + "," + getCc() + "," + getEncoding() + "," + getFrom() + "," + getHasAttachement() + "," + getHeader() + "," + getReceivedOn() + "," + getSentOn() + "," + getSourceCode() + "," + getSubject() + "," + getTo();
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

	public boolean equals(Object o) {
		if (o instanceof Mail) {
			Mail mail = (Mail)o;
			return this.getID().equals(mail.getID());
		}

		return false;
	}
  
  public String[] createMailStrings()
  {
    Vector<String> returnStrings = new Vector<String>();

    // add header, sender, and title
    returnStrings.add(header);
    returnStrings.add(from);
    returnStrings.add(subject);

    String[] splittedBody = body.split();

    // add URL and email in the body
    for(String segment : splittedBody)
    {
      if(segment.contains("http://"))  // URL
      {
        returnStrings.add(segment);
      }
      else if(segment.matches("*@*.*")) // emails
      {
        returnStrings.add(segment);
      }
    }

    return returnStrings;
  }

  public static void main(String[] args)
  {
    Mail mail = new Mail("./emails/email1");

    String[] a = mail.createMailStrings();

    for(String b : a)
    {
      System.out.println(b);
    }
  }

}
