/**
 * 
 */
package com.asuscloud.storage;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import android.util.Base64;
import android.util.Log;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;




/**
 * @author Mirage Lin
 *
 */





public class awsClient {

	/**
	 * 
	 */
	private String tsdBaseURL;
	private String servicePortalURL;
	private String serviceGatewayURL;
	private String infoRelayURL;
	private String webRelayURL;
	private String SID;
	private String progKey;
	private String token;
	private String UserName;
	private String Password;
	
	public String ExceptionMessage;
	public int lastError;
	private String currentTransactionID;
	private String currentChecksum;
	
	
	public awsClient( String sSID, String sProgKey, String sUserName, String sPassword) throws IOException, ParserConfigurationException, SAXException, InterruptedException, ExecutionException, TimeoutException {
		// TODO Auto-generated constructor stub
		super();
		this.progKey = sProgKey;
		this.SID = sSID;
		this.UserName = sUserName;
		this.Password = HashPassword(sPassword);
		this.servicePortalURL = "https://sp.yostore.net";
		acquireToken();
	}
	
	public Boolean PostToHBase(String rawData, String apiDirectory) throws InterruptedException, ExecutionException, TimeoutException, ParserConfigurationException, SAXException, IOException
	{
		
		boolean OK = true;
        this.ExceptionMessage = null;
        this.lastError = 0;
 
		String params[] = new String[5];
	    params[0] = "https://"+this.tsdBaseURL + apiDirectory;
	    params[1] = rawData;
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
	    FutureTask <String> postTask = new FutureTask<String>(new postDataThread(params[0], params[1],params[2],params[3], params[4]) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);
	    
	    String responseXML = null;
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    }
	    
		int status = 0;
		if ( responseXML != null){
			if ( responseXML.indexOf("X-Omni-Status") >= 0 )
			{
				String [] temp = responseXML.split(":");
				this.ExceptionMessage = this.getErrorMsg(Integer.valueOf(temp[1]).intValue());
				OK  = false;
			}
		}
		else
		{
			OK = false;
			this.ExceptionMessage = this.getErrorMsg(990);
		}

		
		return OK;
	}
	
	public String QueryHBase(String rawData, String apiDirectory) throws InterruptedException, ExecutionException, TimeoutException, ParserConfigurationException, SAXException, IOException
	{
		
		boolean OK = true;
        this.ExceptionMessage = null;
        this.lastError = 0;
 
		String params[] = new String[5];
	    params[0] = "https://"+this.tsdBaseURL + apiDirectory;
	    params[1] = rawData;
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
		
	    FutureTask <String> postTask = new FutureTask<String>(new postDataThread(params[0], params[1],params[2],params[3], params[4]) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);
	    
	    String responseXML = null;
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    	
	    	return null;
	    }
	    
		int status = 0;
		if ( responseXML.indexOf("X-Omni-Status") >= 0 )
		{
			String [] temp = responseXML.split(":");
			this.ExceptionMessage = this.getErrorMsg(Integer.valueOf(temp[1]).intValue());
			return null;
		}
		
		return responseXML;
		
		
	}
	
	public String getMySyncFolder() throws ParserConfigurationException, SAXException, IOException
	{
		String getMysyncFolderXML = new StringBuilder().append("<getmysyncfolder><token>").append(this.token).append("</token><userid>").append(this.UserName).append("</userid></getmysyncfolder>").toString();
		String responseXML = null;
		String params[] = new String[5];
	    params[0] = "https://"+this.infoRelayURL + "/folder/getmysyncfolder/";
	    params[1] = getMysyncFolderXML;
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
	    FutureTask <String> postTask = new FutureTask<String>(new postDataThread(params[0], params[1],params[2],params[3], params[4]) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);		
	    
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    }

		if ( responseXML == null)
		{
			return null;
		}
		
		Document dom = this.becomeDom(responseXML);
		
		String status = dom.getElementsByTagName("status").item(0).getTextContent();
		String syncFolderID = null;
		
		if ( status.equals("0") )
		{
			syncFolderID = dom.getElementsByTagName("id").item(0).getTextContent();
		}
		else
		{
			this.lastError = Integer.valueOf(status);
		}
		
		return syncFolderID;
		
	}
	
	public boolean uploadFileFromMemoryStream(String data, String fileName, String folderID, Boolean overWrite)
	{
		Boolean result = true;
		String xmlBrowseResult = null;
		String fileID = "";
		
		
		if (overWrite)
		{
			try{
				xmlBrowseResult = this.browseFolder(folderID);
			}
			catch (ParserConfigurationException pce)
			{
				this.ExceptionMessage = pce.getMessage();
				this.lastError = -1;
				return false;
			}
			catch (SAXException sax)
			{
				this.ExceptionMessage = sax.getMessage();
				this.lastError = -1;
				return false;				
			}
			catch (IOException ioe)
			{
				this.ExceptionMessage = ioe.getMessage();
				this.lastError = -1;
				return false;				
			}
			
			Document dom;
			
			try {
				dom = this.becomeDom(xmlBrowseResult);
			} catch (ParserConfigurationException e) {
				this.ExceptionMessage = e.getMessage();
				this.lastError = -1;
				return false;
			} catch (SAXException e) {
				this.ExceptionMessage = e.getMessage();
				this.lastError = -1;
				return false;
			} catch (IOException e) {
				this.ExceptionMessage = e.getMessage();
				this.lastError = -1;
				return false;
			}
			
			String status = dom.getElementsByTagName("status").item(0).getTextContent();
						
			if ( status.equals("0") )
			{
				
				NodeList files = dom.getElementsByTagName("file");
				String displayName = null;
				String text = null;
				for (int i= 0; i< files.getLength(); i++)
				{
					Node fileNode = files.item(i);
					Element el = (Element)fileNode;
					displayName = el.getElementsByTagName("display").item(0).getTextContent();
					byte[] deCodeData = Base64.decode(displayName, Base64.NO_WRAP);
					try {
						text = new String(deCodeData, "UTF-8");
					} catch (UnsupportedEncodingException e) {
						// TODO Auto-generated catch block
						this.ExceptionMessage = e.getMessage();
						this.lastError = -1;
						return false;
					}
		
					if ( text.equalsIgnoreCase(fileName) )
					{
						fileID = el.getElementsByTagName("id").item(0).getTextContent();
					}
				}
				
				
			}
			else
			{
				this.lastError = Integer.valueOf(status);
				return false;
			}
			
		}

		boolean isOK = true;
		
		try {
			isOK =initbinaryupload(fileName, folderID, data, false, this.currentTransactionID, fileID);
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		}
		
		try {
			isOK =resumebinaryupload( data);
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		}
		
		try {
			isOK = finishbinaryupload();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		}
		return result;
		
	}
	
	
	public boolean uploadFileFromMemoryStream(InputStream stream, String fileName, String folderID, boolean overWrite)
	{
		Boolean result = true;
		String xmlBrowseResult = null;
		String fileID = "";
		
		
		if (overWrite)
		{
			try{
				xmlBrowseResult = this.browseFolder(folderID);
			}
			catch (ParserConfigurationException pce)
			{
				this.ExceptionMessage = pce.getMessage();
				this.lastError = -1;
				return false;
			}
			catch (SAXException sax)
			{
				this.ExceptionMessage = sax.getMessage();
				this.lastError = -1;
				return false;				
			}
			catch (IOException ioe)
			{
				this.ExceptionMessage = ioe.getMessage();
				this.lastError = -1;
				return false;				
			}
			
			Document dom;
			
			try {
				dom = this.becomeDom(xmlBrowseResult);
			} catch (ParserConfigurationException e) {
				this.ExceptionMessage = e.getMessage();
				this.lastError = -1;
				return false;
			} catch (SAXException e) {
				this.ExceptionMessage = e.getMessage();
				this.lastError = -1;
				return false;
			} catch (IOException e) {
				this.ExceptionMessage = e.getMessage();
				this.lastError = -1;
				return false;
			}
			
			String status = dom.getElementsByTagName("status").item(0).getTextContent();
						
			if ( status.equals("0") )
			{
				
				NodeList files = dom.getElementsByTagName("file");
				String displayName = null;
				String text = null;
				for (int i= 0; i< files.getLength(); i++)
				{
					Node fileNode = files.item(i);
					Element el = (Element)fileNode;
					displayName = el.getElementsByTagName("display").item(0).getTextContent();
					byte[] deCodeData = Base64.decode(displayName, Base64.NO_WRAP);
					try {
						text = new String(deCodeData, "UTF-8");
					} catch (UnsupportedEncodingException e) {
						// TODO Auto-generated catch block
						this.ExceptionMessage = e.getMessage();
						this.lastError = -1;
						return false;
					}
		
					if ( text.equalsIgnoreCase(fileName) )
					{
						fileID = el.getElementsByTagName("id").item(0).getTextContent();
					}
				}
				
				
			}
			else
			{
				this.lastError = Integer.valueOf(status);
				return false;
			}
			
		}

		boolean isOK = true;
		
        byte[] buff = new byte[4096];

        int bytesRead = 0;

        ByteArrayOutputStream bao = new ByteArrayOutputStream();


			try {
				while((bytesRead = stream.read(buff)) != -1) {
				   bao.write(buff, 0, bytesRead);
				}
				
				stream.close();
			} catch (IOException e1) {
				this.ExceptionMessage = e1.getMessage();
				this.lastError = -1;
				return false;
			}

        byte[] data = bao.toByteArray();
		
		try {
			isOK =initbinaryupload(fileName, folderID, data, false, this.currentTransactionID, fileID);
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		}
		
		try {
			isOK =resumebinaryupload( data);
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		}
		
		try {
			isOK = finishbinaryupload();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			this.ExceptionMessage = e.getMessage();
			this.lastError = -1;
			return false;
		}
		return result;
		
	}
	
	
	private boolean initbinaryupload(String fileName, String folderID, String data, boolean isResume, String transactionID, String fileID) throws ParserConfigurationException, SAXException, IOException
	{
		this.currentChecksum = null;
		this.currentTransactionID = null;
		
		Long tsLong = System.currentTimeMillis()/1000;
		String timeAttribute =  tsLong.toString();
		
		String atPlainText = new StringBuilder().append("<creationtime>").append(timeAttribute).append("</creationtime>").append("<lastaccesstime>").append(timeAttribute).append("</lastaccesstime>").append("<lastwritetime>").append(timeAttribute).append("</lastwritetime>").toString();
		
		String at = URLEncoder.encode(atPlainText,"UTF-8");
		
		String na = Base64.encodeToString(fileName.getBytes("UTF-8"), Base64.NO_WRAP);
		
		
		String tx ="";
		if (transactionID != null)
		{
			if (transactionID.length() > 0 )
				tx = "&tx=" + transactionID;
		}
		
		
		String fi = "";
		if (fileID != null)
		{
			if (fileID.length() > 0)
			{
				fi = "&fi=" +fileID;
			}
		}		
		
		
		String queryString = new StringBuilder().append("?dis=").append(this.SID).append("&tk=").append(this.token).append("&na=").append(na).append("&pa=").append(folderID).append("&sg=G6243JWEW").append("&at=").append(at).append("&fs=").append(data.length()).append(tx).append(fi).toString();

		String responseXML = null;
		String params[] = new String[5];
	    params[0] = "https://"+this.webRelayURL +"/webrelay/initbinaryupload/" + queryString;
	    params[1] = null;
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
	    FutureTask <String> postTask = new FutureTask<String>(new downloadDataTask(params[0], params[1],params[2],params[3], params[4]) );
	    
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);		
	    
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    	return false;
	    }

		if ( responseXML == null)
		{
			return false;
		}
		
		Document dom = this.becomeDom(responseXML);
		
		String status = dom.getElementsByTagName("status").item(0).getTextContent();
		
		if ( status.equals("0"))
		{
			this.currentTransactionID = dom.getElementsByTagName("transid").item(0).getTextContent();
			
			if (fileID.length() > 0)
			{
				this.currentChecksum = dom.getElementsByTagName("latestchecksum").item(0).getTextContent();
			}
		}
		else
		{
			this.lastError = Integer.valueOf(status);
			return false;
		}
		
		return true;
		

		
	}

	private boolean initbinaryupload(String fileName, String folderID, byte[] data, boolean isResume, String transactionID, String fileID) throws ParserConfigurationException, SAXException, IOException
	{
		this.currentChecksum = null;
		this.currentTransactionID = null;
		
		Long tsLong = System.currentTimeMillis()/1000;
		String timeAttribute =  tsLong.toString();
		
		String atPlainText = new StringBuilder().append("<creationtime>").append(timeAttribute).append("</creationtime>").append("<lastaccesstime>").append(timeAttribute).append("</lastaccesstime>").append("<lastwritetime>").append(timeAttribute).append("</lastwritetime>").toString();
		
		String at = URLEncoder.encode(atPlainText,"UTF-8");
		
		String na = Base64.encodeToString(fileName.getBytes("UTF-8"), Base64.NO_WRAP);
		
		
		String tx ="";
		if (transactionID != null)
		{
			if (transactionID.length() > 0 )
				tx = "&tx=" +transactionID;
		}
		
		
		String fi = "";
		if (fileID != null)
		{
			if (fileID.length() > 0)
			{
				fi = "&fi=" + fileID;
			}
		}		
		
        String queryString = new StringBuilder().append("?dis=").append(this.SID).append("&tk=").append(this.token).append("&na=").append(na).append("&pa=").append(folderID).append("&sg=G6243JWEW").append("&at=").append(at).append("&fs=").append(Integer.valueOf(data.length)).append(tx).append(fi).toString();

		String responseXML = null;
		String params[] = new String[5];
	    params[0] = "https://"+this.webRelayURL +"/webrelay/initbinaryupload/" + queryString;
	    params[1] = null;
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
	    FutureTask <String> postTask = new FutureTask<String>(new downloadDataTask(params[0], params[1],params[2],params[3], params[4]) );
	    
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);		
	    
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    	return false;
	    }

		if ( responseXML == null)
		{
			return false;
		}
		
		Document dom = this.becomeDom(responseXML);
		
		String status = dom.getElementsByTagName("status").item(0).getTextContent();
		
		if ( status.equals("0"))
		{
			this.currentTransactionID = dom.getElementsByTagName("transid").item(0).getTextContent();
			
			if (fileID.length() > 0)
			{
				this.currentChecksum = dom.getElementsByTagName("latestchecksum").item(0).getTextContent();
			}
		}
		else
		{
			this.lastError = Integer.valueOf(status);
			return false;
		}
		
		return true;
		

		
	}
	
	
	private boolean resumebinaryupload(String data) throws ParserConfigurationException, SAXException, IOException
	{
		String queryString = new StringBuilder().append("?dis=").append(this.SID).append("&tk=").append(this.token).append("&tx=").append(this.currentTransactionID).toString();
		String responseXML = null;
		String params[] = new String[5];
	    params[0] = "https://"+this.webRelayURL + "/webrelay/resumebinaryupload/" + queryString;
	    params[1] = data;
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
	    FutureTask <String> postTask = new FutureTask<String>(new postDataThread(params[0], params[1],params[2],params[3], params[4]) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);		
	    
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    }

		if ( responseXML == null)
		{
			
			return false;
		}
		
		Document dom = this.becomeDom(responseXML);
		
		String status = dom.getElementsByTagName("status").item(0).getTextContent();
				
		if ( status.equals("0") )
		{
			this.lastError = 0;
			
		}
		else
		{
			this.lastError = Integer.valueOf(status);
			return false;
		}
		
		return true;
	}

	private boolean resumebinaryupload(byte[] data) throws ParserConfigurationException, SAXException, IOException
	{
		String queryString = new StringBuilder().append("?dis=").append(this.SID).append("&tk=").append(this.token).append("&tx=").append(this.currentTransactionID).toString();
		String responseXML = null;
		
		String params[] = new String[4];
	    params[0] = "https://"+this.webRelayURL + "/webrelay/resumebinaryupload/" + queryString;
	    params[1] = this.SID;
	    params[2] = this.progKey;
	    params[3] = this.token;
	    
	    FutureTask <String> postTask = new FutureTask<String>(new postBinaryDataThread(params[0], params[1],params[2],params[3], data) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);		
	    
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    }

		if ( responseXML == null)
		{
			
			return false;
		}
		
		Document dom = this.becomeDom(responseXML);
		
		String status = dom.getElementsByTagName("status").item(0).getTextContent();
				
		if ( status.equals("0") )
		{
			this.lastError = 0;
			
		}
		else
		{
			this.lastError = Integer.valueOf(status);
			return false;
		}
		
		return true;
	}
	
	
	
	public boolean finishbinaryupload() throws ParserConfigurationException, SAXException, IOException
	{
		if (this.currentTransactionID == null)
			return false;
		if (this.currentTransactionID.length() == 0 )
			return false;
		
		String lsg = "";
		if (this.currentChecksum != null)
		{
			if (this.currentChecksum.length() > 0)
				lsg = "&lsg=" + this.currentChecksum;
		}
		String queryString = new StringBuilder().append("?dis=").append(this.SID).append("&tk=").append(this.token).append("&tx=").append(this.currentTransactionID).append(lsg).toString();
	
		String responseXML = null;
		String params[] = new String[5];
	    params[0] = "https://"+this.webRelayURL +  "/webrelay/finishbinaryupload/" + queryString;
	    params[1] = "";
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
	    FutureTask <String> postTask = new FutureTask<String>(new postDataThread(params[0], params[1],params[2],params[3], params[4]) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);		
	    
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    }

		if ( responseXML == null)
		{
			
			return false;
		}
		
		Document dom = this.becomeDom(responseXML);
		
		String status = dom.getElementsByTagName("status").item(0).getTextContent();
				
		if ( status.equals("0") )
		{
			this.lastError = 0;
			this.currentChecksum = "";
			this.currentTransactionID = "";
			
		}
		else
		{
			this.lastError = Integer.valueOf(status);
			return false;
		}
		
		return true;

	
	}

	public String DownloadFileToString(String FileName, String folderID)
	{
		String fileID = this.getFileID(FileName, folderID);
		if (fileID.length() == 0)
		{
			this.lastError = 0x000000DB;
			return null;
		}
		String queryString = "&fi=" + fileID;

		String responseXML = null;
		String params[] = new String[5];
	    params[0] = "https://"+this.webRelayURL +  "/webrelay/directdownload/" + this.token+"/"+ "?dis=" + this.SID + queryString;
	    params[1] = "";
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
	    Log.d("melissa",params[0]);
	    FutureTask <String> postTask = new FutureTask<String>(new downloadDataTask(params[0], params[1],params[2],params[3], params[4]) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);		
	    
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    	return null;
	    }


		return responseXML;
	}

	public byte[] DownloadFileToStream(String FileName, String folderID)
	{
		String fileID = this.getFileID(FileName, folderID);
		String queryString = "&fi=" + fileID;

		String responseXML = null;
		String params[] = new String[5];
	    params[0] = "https://"+this.webRelayURL +  "/webrelay/directdownload/" + this.token+"/"+ "?dis=" + this.SID + queryString;
	    params[1] = "";
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
	    FutureTask <byte[]> postTask = new FutureTask<byte[]>(new downloadBinaryDataTask(params[0], params[1],params[2],params[3], params[4]) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);		
	    byte[] data = null;
	    try{
	    	data = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    	return null;
	    }


		return data.clone();
	}
	
	
	public String getFolderID(String folderName, String parentFolderID)
	{
		String result = null;
		try {
			result = browseFolder(parentFolderID);
		} catch (ParserConfigurationException e) {
			this.ExceptionMessage = e.getMessage();
			return null;
		} catch (SAXException e) {
			this.ExceptionMessage = e.getMessage();
			return null;
		} catch (IOException e) {
			this.ExceptionMessage = e.getMessage();
			return null;
		}
		
		Document dom;
		String targetfolderID = null;
		
		try {
			dom = this.becomeDom(result);
		} catch (ParserConfigurationException e) {
			this.ExceptionMessage = e.getMessage();
			return null;
		} catch (SAXException e) {
			this.ExceptionMessage = e.getMessage();
			return null;
		} catch (IOException e) {
			this.ExceptionMessage = e.getMessage();
			return null;
		}
		
		String status = dom.getElementsByTagName("status").item(0).getTextContent();
					
		if ( status.equals("0") )
		{
			String text = null;
			
			NodeList folders = dom.getElementsByTagName("folder");
			String displayName = null;
			
			for (int i= 0; i< folders.getLength(); i++)
			{
				Node fileNode = folders.item(i);
				Element el = (Element)fileNode;
				displayName = el.getElementsByTagName("display").item(0).getTextContent();
				
				byte[] data = Base64.decode(displayName, Base64.DEFAULT);
				try {
					text = new String(data, "UTF-8");
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				if ( text.equalsIgnoreCase(folderName) )
				{
					targetfolderID = el.getElementsByTagName("id").item(0).getTextContent();
				}
			}
			
			
		}

		
		return targetfolderID;
		
	}
	
	public String browseFolder(String folderID) throws ParserConfigurationException, SAXException, IOException
	{
		this.lastError = 0;
		String browseFolderXML = new StringBuilder().append("<browse><token>").append(this.token).append("</token><userid>").append(this.UserName).append("</userid><folderid>").append(folderID).append("</folderid><sortby>2</sortby><sortdirection>1</sortdirection></browse>").toString();
		String responseXML = null;
		String params[] = new String[5];
	    params[0] = "https://"+this.infoRelayURL + "/folder/browse/";
	    params[1] = browseFolderXML;
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
	    FutureTask <String> postTask = new FutureTask<String>(new postDataThread(params[0], params[1],params[2],params[3], params[4]) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);		
	    
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    	return null;
	    }

		if ( responseXML == null)
		{
			return null;
		}
		
		Document dom = this.becomeDom(responseXML);
		
		String status = dom.getElementsByTagName("status").item(0).getTextContent();
		
		this.lastError = Integer.valueOf(status);

		
		return responseXML;
	}
	
	public String getLatestChangeFiles() throws ParserConfigurationException, SAXException, IOException
	{
		String getLatestChangeFiles = new StringBuilder().append("<getlatestchangefiles><token>").append(this.token).append("</token><userid>").append(this.UserName).append("</userid><top>1</top><targetroot>-5</targetroot><sortdirection>1</sortdirection></getlatestchangefiles>").toString();
		String responseXML = null;
		String params[] = new String[5];
	    params[0] = "https://"+this.infoRelayURL + "/file/getlatestchangefiles/";
	    params[1] = getLatestChangeFiles;
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
	    FutureTask <String> postTask = new FutureTask<String>(new postDataThread(params[0], params[1],params[2],params[3], params[4]) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);		
	    
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    }

		if ( responseXML == null)
		{
			return null;
		}
		
		Document dom = this.becomeDom(responseXML);
		
		String status = dom.getElementsByTagName("status").item(0).getTextContent();
		if ( !status.equals("0") )
		{
			this.lastError = Integer.valueOf(status);
		}
		
		return responseXML;
		
	}
	
	public String getFileID(String fileName, String folderID)
	{
		String xmlBrowseResult = "";
		String fileID = "";
		try{
			xmlBrowseResult = this.browseFolder(folderID);
		}
		catch (ParserConfigurationException pce)
		{
			this.ExceptionMessage = pce.getMessage();
			return null;
		}
		catch (SAXException sax)
		{
			this.ExceptionMessage = sax.getMessage();
			return null;				
		}
		catch (IOException ioe)
		{
			this.ExceptionMessage = ioe.getMessage();
			return null;				
		}
		
		Document dom;
		
		try {
			dom = this.becomeDom(xmlBrowseResult);
		} catch (ParserConfigurationException e) {
			this.ExceptionMessage = e.getMessage();
			return null;
		} catch (SAXException e) {
			this.ExceptionMessage = e.getMessage();
			return null;
		} catch (IOException e) {
			this.ExceptionMessage = e.getMessage();
			return null;
		}
		
		String status = dom.getElementsByTagName("status").item(0).getTextContent();
					
		if ( status.equals("0") )
		{
			
			NodeList files = dom.getElementsByTagName("file");
			String displayName = null;
			String text = null;
			
			for (int i= 0; i< files.getLength(); i++)
			{
				Node fileNode = files.item(i);
				Element el = (Element)fileNode;
				displayName = el.getElementsByTagName("display").item(0).getTextContent();
				
				byte[] data = Base64.decode(displayName, Base64.DEFAULT);
				try {
					text = new String(data, "UTF-8");
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				if ( text.equalsIgnoreCase(fileName) )
				{
					fileID = el.getElementsByTagName("id").item(0).getTextContent();
				}
			}
			
			
		}
		
		return fileID;
		
	}
	
	public boolean createFolder(String folderName, String ParentFolderID) throws UnsupportedEncodingException
	{
		String timestamp = String.valueOf((long) Calendar.getInstance()
				.getTimeInMillis());
		String attrib = "<creationtime>"+"timestamp"+"</creationtime><lastaccesstime>"+timestamp+"</lastaccesstime><lastwritetime>"+timestamp+"</lastwritetime>";
		
		String na = Base64.encodeToString(folderName.getBytes("UTF-8"), Base64.NO_WRAP);
		
		String xmlcreatefolder = "<create><token>" + this.token + "</token><userid>" + this.UserName + "</userid>" +
                "<parent>" + ParentFolderID + "</parent><isencrypted>0</isencrypted><attribute>"+attrib+"</attribute><display>"+na+"</display><isgroupaware>0</isgroupaware></create>";
		
		String responseXML = null;
		String params[] = new String[5];
	    params[0] = "https://"+this.infoRelayURL + "/folder/create/";
	    params[1] = xmlcreatefolder;
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
	    FutureTask <String> postTask = new FutureTask<String>(new postDataThread(params[0], params[1],params[2],params[3], params[4]) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);		
	    
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    	return false;
	    }

		if ( responseXML == null)
		{
			this.lastError = 999;
			return false;
		}
		
		
		
		
		Document dom;
		
		try {
			dom = this.becomeDom(responseXML);
		} catch (ParserConfigurationException e) {
			this.ExceptionMessage = e.getMessage();
			return false;
		} catch (SAXException e) {
			this.ExceptionMessage = e.getMessage();
			return false;
		} catch (IOException e) {
			this.ExceptionMessage = e.getMessage();
			return false;
		}
		
		String status = dom.getElementsByTagName("status").item(0).getTextContent();
		boolean isOK = true;
		this.lastError = Integer.valueOf(status);
		if ( status.equals("0") )
			isOK = true;
		else
			isOK = false;
		
		return isOK;
	}
	
	public boolean removeFolder(String folderID)
	{
		String xmlRemovefolder = "<remove><token>" + this.token + "</token><userid>" + this.UserName + "</userid>" +
                "<id>" + folderID + "</id><ischildonly>0</ischildonly><isgroupaware>0</isgroupaware></remove>";
		
		
		String responseXML = null;
		String params[] = new String[5];
	    params[0] = "https://"+this.infoRelayURL + "/folder/remove/";
	    params[1] = xmlRemovefolder;
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
	    FutureTask <String> postTask = new FutureTask<String>(new postDataThread(params[0], params[1],params[2],params[3], params[4]) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);		
	    
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    	return false;
	    }

		if ( responseXML == null)
		{
			this.lastError = 999;
			return false;
		}
		
		
		
		
		Document dom;
		
		try {
			dom = this.becomeDom(responseXML);
		} catch (ParserConfigurationException e) {
			this.ExceptionMessage = e.getMessage();
			return false;
		} catch (SAXException e) {
			this.ExceptionMessage = e.getMessage();
			return false;
		} catch (IOException e) {
			this.ExceptionMessage = e.getMessage();
			return false;
		}
		
		String status = dom.getElementsByTagName("status").item(0).getTextContent();
		boolean isOK = true;
		this.lastError = Integer.valueOf(status);
		if ( status.equals("0") )
			isOK = true;
		else
			isOK = false;
		
		return isOK;

	}
	
	
	public boolean removeFile(String[] fileIDs)
	{
		String xmlBrowseResult = "";
		String fileIDList = "";
		
		if (fileIDs == null)
		{
			return false;
		}
		
		if (fileIDs.length == 0)
		{
			return false;
		}
		if (fileIDs.length > 1)
		{
			for (int i = 0; i< fileIDs.length; i++)
			{
				fileIDList = fileIDList + fileIDs[i].toString();
				if (i < (fileIDs.length - 1) )
				{
					fileIDList = fileIDList + ",";
				}
				
			}
		}
		else
		{
			fileIDList = fileIDs[0].toString();
		}
		
		String xmlRemoveFile = "<remove><token>" + this.token + "</token><userid>" + this.UserName + "</userid>" +
                "<id>" + fileIDList + "</id></remove>";
		
		String responseXML = null;
		String params[] = new String[5];
	    params[0] = "https://"+this.infoRelayURL + "/file/remove/";
	    params[1] = xmlRemoveFile;
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
	    FutureTask <String> postTask = new FutureTask<String>(new postDataThread(params[0], params[1],params[2],params[3], params[4]) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);		
	    
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    	return false;
	    }

		if ( responseXML == null)
		{
			this.lastError = 999;
			return false;
		}

		Document dom;
		
		try {
			dom = this.becomeDom(responseXML);
		} catch (ParserConfigurationException e) {
			this.ExceptionMessage = e.getMessage();
			return false;
		} catch (SAXException e) {
			this.ExceptionMessage = e.getMessage();
			return false;
		} catch (IOException e) {
			this.ExceptionMessage = e.getMessage();
			return false;
		}
		
		String status = dom.getElementsByTagName("status").item(0).getTextContent();
		boolean isOK = true;
		this.lastError = Integer.valueOf(status);
		if ( status.equals("0") )
			isOK = true;
		else
			isOK = false;
		
		return isOK;
		
	}
	
	protected String deletesharecode(String id) {
		String params[] = new String[2];
		params[0] = "https://" + this.infoRelayURL + "/fsentry/deletesharecode/";
		params[1] = "<deletesharecode><token>"+this.token+"</token><scrip>"+getScrip()+
				    "</scrip><userid>"+this.UserName+"</userid><entrytype>0</entrytype><entryid>"+id+
				    "</entryid><password></password></deletesharecode>";
		return sendRequest(params);
	}
	
	protected String setadvancedsharecode(String id, String password, String isFolder) {
		String params[] = new String[2];
		params[0] = "https://" + this.infoRelayURL + "/fsentry/setadvancedsharecode/";
		params[1] = "<setadvancedsharecode><token>"+this.token+"</token><userid>"+this.UserName+
				    "</userid><isfolder>"+isFolder+"</isfolder><entryid>"+id+"</entryid><clearshare>0</clearshare>"+
				    "<clearpassword>0</clearpassword><clearvalidityduration>0</clearvalidityduration>"+
				    "<releasequotalimit>0</releasequotalimit><password>"+HashPassword(password)+
				    "</password></setadvancedsharecode>";
		return sendRequest(params);
	}
	
	/*failed, return payload invalid*/
	/*protected String getsharedcode(String id, String password, String isFile) {
		String params[] = new String[2];
		params[0] = "https://" + this.infoRelayURL + "/fsentry/getsharecode/";
		params[1] = "<getsharecode><token>" +this.token +"</token><scrip>"+getScrip()+"</scrip><userid>"+this.UserName+
				    "</userid><entrytype>"+ isFile +"</entrytype><entryid>"+id+"</entryid><password>"+HashPassword(password)+
				    "</password><actiontype>0</actiontype><isnewurlenc>0</isnewurlenc></getsharecode>";
		return sendRequest(params);
	}*/
	
	/*
	 * Author by Melissa.
	 * 用目錄ID查詢folder or file的分享狀態
	 */
	protected String getShareStatus(String id, String isFolder) {
		String params[] = new String[2];
		params[0] = "https://" + this.infoRelayURL + "/fsentry/getadvancedsharecode/";
		params[1] = "<getadvancedsharecode><token>"+ this.token +"</token><userid>"
		             +this.UserName+"</userid><isfolder>"+isFolder+"</isfolder><entryid>"+id+"</entryid></getadvancedsharecode>";
		return sendRequest(params);
	}
	/*
	 * Author by Melissa.
	 * 用目錄ID查詢folder下的分享狀態
	 */
	protected String getshareentry(String id) {
		String API = "/fsentry/getchildrensharedentries/";
		String params[] = new String[2];
		params[0] = "https://" + this.infoRelayURL + API;
		params[1] = "<getchildrensharedentries><token>"+ this.token +"</token><userid>"
		             +this.UserName+"</userid><ffid>"+id+"</ffid></getchildrensharedentries>";
		return sendRequest(params);
	}
		
	/*
	 * Author by Melissa.
	 * Add getPhotoMetaData protected method to get photo's EXIF data.
	 */
	protected String getPhotoMetaData(String filepath, String folderID) {
		String queryString = "&fi=" + this.getFileID(filepath, folderID);
		String params[] = new String[2];
	    params[0] = "https://"+this.webRelayURL + "/webrelay/getmediametadata/" + this.token + "/" + "?dis=" + this.SID + queryString;
	    params[1] = "";
	    return sendRequest(params);
	}
	
	
	private String getErrorMsg(int status)
	{
		String msg;
		switch (status)
		{
        case 2:
            msg = "Authentication Fail";
            break;
        case 5:
            msg = "Authorization Fail";
            break;
        
        case 201:
            msg = "AUTH Input Data FAIL";
            break;

        case 300:
            msg = "Stream Exception";
            break;

        case 301:
            msg = "Xml Stream Exception";
            break;

        case 310:
            msg = "Required Field Validator Exception";
            break;
        
        case 311:
            msg = "Field Format Exception";
            break;
        
        case 404:
            msg = "Schema Not Found";
            break;

        case 405:
            msg = "Action Not Support";
            break;

        case 990:
        	msg = "Time Out";
        	break;
        case 999:
            msg = "A general Erro";
            break;
        default:
            msg = "UnExpected Error";
            break;
		
		}
		
		return msg;
	}
	
    private String HashPassword(String sPassword){
    	if (sPassword == null)
    		return null;
    	
        MessageDigest md = null;
        sPassword = sPassword.toLowerCase();
        try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
        byte[] md5hash = new byte[32];
        try {
			md.update(sPassword.getBytes("utf-8"), 0, sPassword.length());
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
        md5hash = md.digest();
        return convertToHex(md5hash);
    	
    }
    
    private String tripleDesPassword(String sPassword)
    {
    	DESedeEncoder tDes = new DESedeEncoder();
    	String encryptStr = tDes.encryptThreeDESECB(sPassword, this.progKey);
    	
    	return encryptStr;
    }
    
	private static String convertToHex(byte[] data) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
            int halfbyte = (data[i] >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                if ((0 <= halfbyte) && (halfbyte <= 9))
                    buf.append((char) ('0' + halfbyte));
                else
                    buf.append((char) ('a' + (halfbyte - 10)));
                halfbyte = data[i] & 0x0F;
            } while(two_halfs++ < 1);
        }
        return buf.toString();
    }


	private Boolean acquireToken() throws IOException, ParserConfigurationException, SAXException, InterruptedException, ExecutionException, TimeoutException{
	
		if (this.serviceGatewayURL == null)
		{
			try {
				requestGateway();
				if (this.serviceGatewayURL == null)
					return false;
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				this.ExceptionMessage = e.getMessage();
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				this.ExceptionMessage = e.getMessage();
			} catch (TimeoutException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				this.ExceptionMessage = e.getMessage();
			}
		}
		
		
		String acquireTokenXML = "<aaa><userid>" + this.UserName + "</userid><password>" + this.Password + "</password><time>2008/1/1</time></aaa>";
		
		String params[] = new String[4];
	    params[0] = "https://"+this.serviceGatewayURL + "/member/acquiretoken/";
	    params[1] = acquireTokenXML;
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    
	    FutureTask <String> postTask = new FutureTask<String>(new postDataThread(params[0], params[1],params[2],params[3], null) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);
	    
	    String responseXML = null;
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    }

		if ( responseXML == null)
		{
			return false;
		}
		
		InputStream is = new ByteArrayInputStream(responseXML.getBytes("UTF-8"));
		DocumentBuilder builder;
		
		DocumentBuilderFactory  factory = DocumentBuilderFactory.newInstance();
		builder = factory.newDocumentBuilder();
		Document dom = builder.parse(is);
		
		this.token = dom.getElementsByTagName("token").item(0).getTextContent();
		this.tsdBaseURL = dom.getElementsByTagName("tsdbase").item(0).getTextContent();
		this.infoRelayURL = dom.getElementsByTagName("inforelay").item(0).getTextContent();
		this.webRelayURL = dom.getElementsByTagName("webrelay").item(0).getTextContent();

		
		
		
		return true;
	}
	
	private Document becomeDom( String sourceXML) throws ParserConfigurationException, SAXException, IOException
	{
		InputStream is = new ByteArrayInputStream(sourceXML.getBytes("UTF-8"));
		DocumentBuilder builder;
		
		DocumentBuilderFactory  factory = DocumentBuilderFactory.newInstance();
		builder = factory.newDocumentBuilder();
		Document dom = builder.parse(is);
		return dom;
	}
	
 	private boolean requestGateway() throws IOException, ParserConfigurationException, SAXException, InterruptedException, ExecutionException, TimeoutException{
	
		String xmlRequestGateway = "<requestservicegateway><userid>" + this.UserName + "</userid><password>" + this.Password + "</password><language></language><service>1</service>></requestservicegateway>";
		String params[] = new String[4];
	    params[0] = this.servicePortalURL + "/member/requestservicegateway/";
	    params[1] = xmlRequestGateway;
	    params[2] = this.SID;
	    params[3] = this.progKey;
		
	    FutureTask <String> postTask = new FutureTask<String>(new postDataThread(params[0], params[1],params[2],params[3], null) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);
	    
	    String responseXML = null;
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    	return false;
	    }
		//String responseXML = postTask.execute(params).get(60000, TimeUnit.MILLISECONDS);
	    if (responseXML == null )
			return false;
		InputStream is = new ByteArrayInputStream(responseXML.getBytes("UTF-8"));
		DocumentBuilder builder;
		
		DocumentBuilderFactory  factory = DocumentBuilderFactory.newInstance();
		builder = factory.newDocumentBuilder();
		Document dom = builder.parse(is);

		Element el = (Element)(dom.getElementsByTagName("servicegateway").item(0));
		
		this.serviceGatewayURL = el.getTextContent();
		return true;
	}
 	
 	/* Author by Melissa
 	 * Add a sendRequest method to module code. 
 	 */
 	private String sendRequest(String[] array) {
 		String params[] = new String[5];
 		params[0] = array[0];
 		params[1] = array[1];
	    params[2] = this.SID;
	    params[3] = this.progKey;
	    params[4] = this.token;
	    
 		String responseXML = null;
 		FutureTask <String> postTask = new FutureTask<String>(new postDataThread(params[0], params[1],params[2],params[3], params[4]) );
	    ExecutorService es = Executors.newSingleThreadExecutor();
	    es.submit(postTask);		
	    
	    try{
	    	responseXML = postTask.get(180000, TimeUnit.MILLISECONDS);
	    }
	    catch (Exception e)
	    {
	    	this.ExceptionMessage = e.getMessage();
	    }
		return responseXML;
 	}
 	
 	private String getScrip(){
 		return String.valueOf(System.currentTimeMillis());
 	}
	
	
}
