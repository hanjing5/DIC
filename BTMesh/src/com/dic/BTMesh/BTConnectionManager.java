package com.dic.BTMesh;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class BTConnectionManager extends Activity {

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	
	    TextView textview = new TextView(this);
	    textview.setText("This is the ConnectionManager");
	    setContentView(textview);
	}

}