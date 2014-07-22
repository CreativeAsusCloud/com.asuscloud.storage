/**
 * 
 */
package com.asuscloud.storage;

import android.os.Handler;
import android.os.Message;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;


/**
 * @author Mirage Lin
 *
 */


public class getLatestChangeTask extends Service{
	private ACSDocumentManager acs;
	
	private Handler handler = new Handler();

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}  


    @Override
    public void onStart(Intent intent, int startId) {
        // TODO Auto-generated method stub
    	

    	String sid = "14543061";
		String progKey = "15CB2D4267E94B8E82A9073EAAC3183F";
		String username =  "rogerchen";
		String password = "1qaz@WSX";
		
		//³s±µ¶³ºÝ
		this.acs = new ACSDocumentManager(sid, progKey, username, password);
		
		handler.postDelayed(showTime, 1000);
		
    }
    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        
    }
 
    private Runnable showTime = new Runnable() {

    	public void run() {
    		/*Intent i;
    		i = new Intent("android.intent.action.MAIN").putExtra("notification", "Has New File!");
    		
    		boolean f= acs.hasNewJpeg();
			if (f)
			{
				MainActivity.thisActivity.sendBroadcast(i);					
			}    			
    	    handler.postDelayed(this, 2000);
			*/
    	}

    	};          
    }  

