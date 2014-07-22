package com.asuscloud.storage;

import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.HashMap;

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
public class ACSDocumentManager {
	
    private awsClient awsObj;
    private ACS_ErrorCode lastError;
    private String mySyncFolderID;
    private String myBackFolderID;
    private String myCollectionFolderID;
    private String lastFilename;
    private static int STATUS_OK = 0;
    
    public int getErrorCode()
    {
    	return lastError.value();
    }
	
	public ACSDocumentManager(String SID, String ProgKey, String Username, String Password)
	{
		this.awsObj = null;
		this.lastError = ACS_ErrorCode.OK;
		
        if ( (SID.length() == 0) || (ProgKey.length() == 0) )
        {
            lastError = ACS_ErrorCode.SID_PROGKEY_IS_NULL;
            return;
        }

        if ( (Username.length() == 0) || ( Password.length() ==0 ) )
        {
            lastError = ACS_ErrorCode.USERNAME_PASSWORD_IS_NULL;
            return;
        }
			try {
				this.awsObj = new awsClient(SID, ProgKey, Username, Password);
			} catch (IOException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return;
			} catch (ParserConfigurationException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return;
			} catch (SAXException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return;
			} catch (InterruptedException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return;
			} catch (ExecutionException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return;
			} catch (TimeoutException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return;
			}

			try {
				this.mySyncFolderID = awsObj.getMySyncFolder();
			} catch (ParserConfigurationException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return;
			} catch (SAXException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return;
			} catch (IOException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return;
			}      
	}
    
	public ACSObject[] browseFolder(String Path, ACS_LIST_OPTION Option)
	{
		String[] paths = getPathList(Path);
		if (paths == null)
			return null;
		
		String folderid = getLastFolderID(paths);
		String xmlBrowseFolder;
		
		try {
			xmlBrowseFolder = awsObj.browseFolder(folderid);
		} catch (ParserConfigurationException e) {
			this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
			return null;
		} catch (SAXException e) {
			this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
			return null;
		} catch (IOException e) {
			this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
			return null;
		}

		
		Document dom;

			try {
				dom = this.becomeDom(xmlBrowseFolder);
			} catch (ParserConfigurationException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return null;
			} catch (SAXException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return null;
			} catch (IOException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return null;
			}

		
		NodeList files = dom.getElementsByTagName("file");
		NodeList folders = dom.getElementsByTagName("folder");
		int total_rows = 0;
		
		switch (Option.value()){
		case 0:
			//all
			total_rows = files.getLength() + folders.getLength();
			break;
		case 1:
			//fileonly
			total_rows = files.getLength();
			break;
		case 0x10:
			total_rows = folders.getLength();
			break;
		default:
			total_rows = 0;
			break;
		
		}
		
		if (total_rows == 0)
			return null;
		
		ACSObject[] returnObjs = new ACSObject[total_rows];
		int index = 0;
		
		if (Option == ACS_LIST_OPTION.ALL || Option == ACS_LIST_OPTION.DIRECTORY_ONLY)
		{
			String displayName = null;
			for (int i = 0; i< folders.getLength(); i++)
			{
				Node tmpNode = folders.item(i);
				Element el = (Element)tmpNode;
				
				ACSObject obj = new ACSObject();
				
				String text=null;
				displayName = el.getElementsByTagName("display").item(0).getTextContent();
				byte[] deCodeData = Base64.decode(displayName, Base64.NO_WRAP);
				try {
					text = new String(deCodeData, "UTF-8");
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
					return null;
				}
				obj.setObjName(text);
				
				obj.setFolderTreeSize( Integer.valueOf(el.getElementsByTagName("treesize").item(0).getTextContent()) );
				obj.setObjID(el.getElementsByTagName("id").item(0).getTextContent());
				obj.setObjCreatedTime(el.getElementsByTagName("createdtime").item(0).getTextContent());
				
				if ( el.getElementsByTagName("ispublic").item(0).getTextContent().equals("1") )
				{
					obj.setIsShared(true);
				}
				else
				{
					obj.setIsShared(false);
				}
				
				if ( el.getElementsByTagName("isbackup").item(0).getTextContent().equals("1") )
				{
					obj.setIsBackup(true);
				}
				else
				{
					obj.setIsBackup(false);
				}
				
				if ( el.getElementsByTagName("isgroupaware").item(0).getTextContent().equals("1") )
				{
					obj.setIsGroupAware(true);
				}
				else
				{
					obj.setIsGroupAware(false);
				}
				
				obj.setIsFileType(false);
				
                returnObjs[index] = obj;
                index++;
				
			}			
		}
		
		if (Option == ACS_LIST_OPTION.ALL || Option == ACS_LIST_OPTION.FILE_ONLY)
		{
			String displayName = null;
			for (int i = 0; i< files.getLength(); i++)
			{
				Node tmpNode = files.item(i);
				Element el = (Element)tmpNode;
				
				ACSObject obj = new ACSObject();
				
				String text=null;
				displayName = el.getElementsByTagName("display").item(0).getTextContent();
				byte[] deCodeData = Base64.decode(displayName, Base64.NO_WRAP);
				try {
					text = new String(deCodeData, "UTF-8");
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
					return null;
				}
				obj.setObjName(text);
				
				
				obj.setFileSize( Integer.valueOf(el.getElementsByTagName("size").item(0).getTextContent()) );
				obj.setObjID(el.getElementsByTagName("id").item(0).getTextContent());
				obj.setObjCreatedTime(el.getElementsByTagName("createdtime").item(0).getTextContent());
				
				if ( el.getElementsByTagName("ispublic").item(0).getTextContent().equals("1") )
				{
					obj.setIsShared(true);
				}
				else
				{
					obj.setIsShared(false);
				}
				
				if ( el.getElementsByTagName("isbackup").item(0).getTextContent().equals("1") )
				{
					obj.setIsBackup(true);
				}
				else
				{
					obj.setIsBackup(false);
				}
				
				if ( el.getElementsByTagName("isgroupaware").item(0).getTextContent().equals("1") )
				{
					obj.setIsGroupAware(true);
				}
				else
				{
					obj.setIsGroupAware(false);
				}
				
				obj.setIsFileType(true);
				
                returnObjs[index] = obj;
                index++;
				
			}
			
		}		
		
		
		return returnObjs;
	}
	
	
    public boolean write(InputStream stream, String cloudFilename, boolean overWrite)
    {
    	boolean result = true;
        String[] allPath = getPathList(cloudFilename);
        
        if (allPath.length < 3)
        	allPath = getPathList("/Mysync/" + cloudFilename);
        
        String[] paths = new String[allPath.length -1];
        for (int i=0; i < allPath.length-1; i++)
        {
        	paths[i] = allPath[i];
        }
        
        String folderid = getLastFolderID(paths);        

		result = awsObj.uploadFileFromMemoryStream(stream, allPath[allPath.length-1], folderid, overWrite);

        
        if (result)
        	this.lastError = ACS_ErrorCode.OK;
        else
        	this.lastError = ACS_ErrorCode.valueOf(awsObj.lastError);
        	
        return result;    	

    }
    
    public boolean write(String data, String cloudFilename, boolean overWrite)
    {
    	boolean result = true;
        String[] allPath = getPathList(cloudFilename);
        
        if (allPath.length < 3)
        	allPath = getPathList("/Mysync/" + cloudFilename);
        
        String[] paths = new String[allPath.length -1];
        for (int i=0; i < allPath.length-1; i++)
        {
        	paths[i] = allPath[i];
        }
        
        String folderid = getLastFolderID(paths);        

        result = awsObj.uploadFileFromMemoryStream(data, allPath[allPath.length-1], folderid, overWrite);
        
        if (result)
        	this.lastError = ACS_ErrorCode.OK;
        else
        	this.lastError = ACS_ErrorCode.valueOf(awsObj.lastError);
        	
        return result;    	
    }
	
    public boolean removeFile(String cloudFilename)
    {
    	boolean result = false;
        String[] allPath = getPathList(cloudFilename);
        
        if (allPath.length < 3)
        	allPath = getPathList("/Mysync/" + cloudFilename);
        
        String[] paths = new String[allPath.length -1];
        for (int i=0; i < allPath.length-1; i++)
        {
        	paths[i] = allPath[i];
        }
        
        String folderid = getLastFolderID(paths);
        String xmlBrowse =null; 

			try {
				xmlBrowse = awsObj.browseFolder(folderid);
			} catch (ParserConfigurationException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return false;
			} catch (SAXException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return false;
			} catch (IOException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return false;
			}

        
        if (xmlBrowse == null)
        {
        	this.lastError = ACS_ErrorCode.FILE_NOT_EXIST_OR_DELETED;
        	return false;
        }
        
        String[] targetFileID = new String[1];
        
        targetFileID[0] = getIDByName(allPath[allPath.length-1], xmlBrowse);
        
        result = awsObj.removeFile(targetFileID);
        
        if (!result)
        	this.lastError = ACS_ErrorCode.FILE_NOT_EXIST_OR_DELETED;
        else
        	this.lastError = ACS_ErrorCode.OK;
        
        return result;
    }
    
    public boolean removeFolder(String cloudFoldername)
    {
    	boolean result = false;
        String[] allPath = getPathList(cloudFoldername);
        
        if (allPath.length < 3)
        	allPath = getPathList("/Mysync/" + cloudFoldername);
        
        String[] paths = new String[allPath.length -1];
        for (int i=0; i < allPath.length-1; i++)
        {
        	paths[i] = allPath[i];
        }
        
        String folderid = getLastFolderID(paths);
        String xmlBrowse =null; 

			try {
				xmlBrowse = awsObj.browseFolder(folderid);
			} catch (ParserConfigurationException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return false;
			} catch (SAXException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return false;
			} catch (IOException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return false;
			}

        
        if (xmlBrowse == null)
        {
        	this.lastError = ACS_ErrorCode.FILE_NOT_EXIST_OR_DELETED;
        	return false;
        }
        
        
        String targetFileID = getForlderIDByName(allPath[allPath.length-1], xmlBrowse);
        
        result = awsObj.removeFolder(targetFileID);
        
        if (!result)
        	this.lastError = ACS_ErrorCode.FILE_NOT_EXIST_OR_DELETED;
        else
        	this.lastError = ACS_ErrorCode.OK;
        
        return result;
    	
    }
    
    
    public boolean createFolder(String cloudFoldername) throws UnsupportedEncodingException
    {
    	boolean result = false;
        String[] allPath = getPathList(cloudFoldername);
        
        if (allPath.length < 3)
        	allPath = getPathList("/Mysync/" + cloudFoldername);
        
        String[] paths = new String[allPath.length -1];
        for (int i=0; i < allPath.length-1; i++)
        {
        	paths[i] = allPath[i];
        }
        
        String folderid = getLastFolderID(paths);
        String xmlBrowse =null; 

			try {
				xmlBrowse = awsObj.browseFolder(folderid);
			} catch (ParserConfigurationException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return false;
			} catch (SAXException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return false;
			} catch (IOException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return false;
			}

        
        if (xmlBrowse == null)
        {
        	this.lastError = ACS_ErrorCode.FILE_NOT_EXIST_OR_DELETED;
        	return false;
        }
        
        
        String targetFileID ="";
        if ( allPath[allPath.length-2].equalsIgnoreCase("mysync"))
        {
			try {
				targetFileID = awsObj.getMySyncFolder();
			} catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			}
        }
        else
        	targetFileID = getForlderIDByName(allPath[allPath.length-2], xmlBrowse);
        
        
        if (targetFileID != null)
        	result = awsObj.createFolder( allPath[allPath.length-1], targetFileID);
        
        if (!result)
        	this.lastError = ACS_ErrorCode.FILE_NOT_EXIST_OR_DELETED;
        else
        	this.lastError = ACS_ErrorCode.OK;
        
        return result;
    }
    

    
    
    public String readTextFile(String cloudFilename)
    {
    	
    	String[] allPath = getPathList(cloudFilename);
        
        if (allPath.length < 3)
        	allPath = getPathList("/Mysync/" + cloudFilename);
        
        String[] paths = new String[allPath.length -1];
        for (int i=0; i < allPath.length-1; i++)
        {
        	paths[i] = allPath[i];
        }
        
        String folderid = getLastFolderID(paths);     	
    	String text = awsObj.DownloadFileToString(allPath[allPath.length-1], folderid);
    	
    	if (text == null)
    	{
    		this.lastError = ACS_ErrorCode.valueOf(awsObj.lastError);
    	}
    	else
    		this.lastError = ACS_ErrorCode.OK;
    	
    	return text;
    	
    }

    public byte[] read(String cloudFilename)
    {
    	String[] allPath = getPathList(cloudFilename);
        
        if (allPath.length < 3)
        	allPath = getPathList("/Mysync/" + cloudFilename);
        
        String[] paths = new String[allPath.length -1];
        for (int i=0; i < allPath.length-1; i++)
        {
        	paths[i] = allPath[i];
        }
        
        String folderid = getLastFolderID(paths);     	
    	byte[] data = null;
    	data= awsObj.DownloadFileToStream(allPath[allPath.length-1], folderid);
    	
    	if (data == null)
    	{
    		this.lastError = ACS_ErrorCode.valueOf(awsObj.lastError);
    	}
    	else
    		this.lastError = ACS_ErrorCode.OK;
    	
    	return data.clone();
    	
    }
    
    private String [] getPathList(String fullPath)
    {

        String[] paths = fullPath.split("/");
        return paths;

    }
	
    
	private String getLastFolderID(String[] paths)
    {
        int startFolderID = 0;
        //get initial FolderID
        String lastFolderID = null;

        if (paths[1].toLowerCase(Locale.getDefault()).equals("mysync") ) 
        {
				try {
					lastFolderID = awsObj.getMySyncFolder();
				} catch (ParserConfigurationException e) {
					this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
					return null;
				} catch (SAXException e) {
					this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
					return null;
				} catch (IOException e) {
					this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
					return null;
				}
        }
        else
        {
        	return null;
        }

        String result = null;

        if (paths.length > 2 )
        {
            for (int i=2; i<paths.length;i++)
            {

					try {
						result = awsObj.browseFolder(lastFolderID);
					} catch (ParserConfigurationException e) {
						this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
						return null;
					} catch (SAXException e) {
						this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
						return null;
					} catch (IOException e) {
						this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
						return null;
					}

                lastFolderID =  getForlderIDByName(paths[i], result);
            }
        }
        else
        {
            return lastFolderID.toString();
        }

        return lastFolderID;
    }

	private String getIDByName(String fileName, String xmlResult) {
		// TODO Auto-generated method stub
		
		Document dom = null;


			try {
				dom = this.becomeDom(xmlResult);
			} catch (ParserConfigurationException e1) {
				this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
				return null;
			} catch (SAXException e1) {
				this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
				return null;
			} catch (IOException e1) {
				this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
				return null;
			}

		String fileID = null;

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

	
	private String getForlderIDByName(String folderName, String xmlResult) {
		// TODO Auto-generated method stub
		
		Document dom = null;


			try {
				dom = this.becomeDom(xmlResult);
			} catch (ParserConfigurationException e1) {
				this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
				return null;
			} catch (SAXException e1) {
				this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
				return null;
			} catch (IOException e1) {
				this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
				return null;
			}

		String fileID = null;

		String status = dom.getElementsByTagName("status").item(0).getTextContent();
					
		if ( status.equals("0") )
		{
			
			NodeList files = dom.getElementsByTagName("folder");
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
				
				if ( text.equalsIgnoreCase(folderName) )
				{
					fileID = el.getElementsByTagName("id").item(0).getTextContent();
				}
			}
			
			
		}
		
		return fileID;		
		
	}
	
	public boolean hasNewJpeg()
	{
		String xmlLatestChange;
		try {
			xmlLatestChange = awsObj.getLatestChangeFiles();
		} catch (ParserConfigurationException e) {
			this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
			return false;
		} catch (SAXException e) {
			this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
			return false;
		} catch (IOException e) {
			this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
			return false;
		}

		
		Document dom;

			try {
				dom = this.becomeDom(xmlLatestChange);
			} catch (ParserConfigurationException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return false;
			} catch (SAXException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return false;
			} catch (IOException e) {
				this.lastError =  ACS_ErrorCode.EXCEPTION_OCCURS;
				return false;
			}

		
		NodeList files = dom.getElementsByTagName("entry");
		Node fileNode = files.item(0);
		Element el = (Element)fileNode;
		String filename = el.getElementsByTagName("rawfilename").item(0).getTextContent();
		boolean isFileComing = false;
		
		if (!filename.equals(this.lastFilename) )
		{
			this.lastFilename = filename;
			isFileComing = true;
		}
			
		return isFileComing;
	}
	
	
	private Document becomeDom(String sourceXML, boolean canCatch) {
		Document dom = null;
		try {
			InputStream is = new ByteArrayInputStream(sourceXML.getBytes("UTF-8"));
			DocumentBuilder builder;
		
			DocumentBuilderFactory  factory = DocumentBuilderFactory.newInstance();
			builder = factory.newDocumentBuilder();
			dom = builder.parse(is);
		} catch (ParserConfigurationException e1) {
			this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
			return null;
		} catch (SAXException e1) {
			this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
			return null;
		} catch (IOException e1) {
			this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
			return null;
		}
		return dom;
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
	
	/*
	 * Author by Melissa
	 * get Status code
	 * @returnType integer
	 */
	private int checkStatus(Document dom) {
		int status = Integer.parseInt(dom.getElementsByTagName("status").item(0).getTextContent());
		return status;
	}
	
	/*
	 * Author by Melissa
	 * 1. get folder ID
	 * 2. use folder id to get file id
	 */
	private String getFileID(String filepath) {
		String filename = filepath.substring(filepath.lastIndexOf("/")+1);
		String folderID = getFolderID(filepath, false);
		String xmlResult = null;
		String fileID = null;
		try {
			xmlResult = awsObj.browseFolder(folderID);
		} catch (Exception e) {
			this.lastError=ACS_ErrorCode.EXCEPTION_OCCURS;
		}
		Document dom = this.becomeDom(xmlResult, true);
		int status = checkStatus(dom);
		if (status == STATUS_OK){
    		NodeList files = dom.getElementsByTagName("file");
    		for (int j = 0; j< files.getLength();j++) {
    			Node tmpNode = files.item(j);
				Element el = (Element)tmpNode;
				if (FilenameDecode(el.getElementsByTagName("display").item(0).getTextContent()).equals(filename)){
					fileID = el.getElementsByTagName("id").item(0).getTextContent();
				}
    		}
		}
		return fileID;
	}
	
	/*
	 * Author by Melissa
	 * get folder ID by folder path
	 */
	private String getFolderID(String folderpath) {
		return getFolderID(folderpath, true);
	}
	
	/*
	 * Author by Melissa
	 * 1. Use getFileID to get file id by filepath. 
	 * 2. Use getFolderID & isFolder = false to get folder id by filepath.
	 * 3. Use getFolderID to get folder id by folder path.
	 */
	private String getFolderID(String folderpath, boolean isFolder) {
		String pathList[] = ACS_File.getPathList(folderpath, isFolder);
		String lastID = null;
		String xmlResult = null;
		for (int i = 0; i< pathList.length-1; i++) {
		    if (pathList[i].equals("mysync")) {
		    	lastID = this.mySyncFolderID;
		    }
		    if (lastID != null) {
		    	try {
		    	    xmlResult = awsObj.browseFolder(lastID);
		    	} catch (Exception e) {
		    		this.lastError=ACS_ErrorCode.EXCEPTION_OCCURS;
		    	}
		    	Document dom = this.becomeDom(xmlResult, true);
		    	int status = checkStatus(dom);
		    	if (status == STATUS_OK){
		    		NodeList folders = dom.getElementsByTagName("folder");
		    		for (int j = 0; j< folders.getLength();j++) {
		    			Node tmpNode = folders.item(j);
						Element el = (Element)tmpNode;
						if (FilenameDecode(el.getElementsByTagName("display").item(0).getTextContent()).equals(pathList[i+1])){
							lastID = el.getElementsByTagName("id").item(0).getTextContent();
						}
		    		}
				}
		    }
		}
		return lastID;
	}
	/*
	 * Author by Melissa
	 * return decrypt filename from filename by base64
	 */
	private String FilenameDecode(String displayName) {
		String text = null;
		byte[] deCodeData = Base64.decode(displayName, Base64.NO_WRAP);
		try {
			text = new String(deCodeData, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			this.lastError = ACS_ErrorCode.EXCEPTION_OCCURS;
			return null;
		}
		return text;
	}
}
