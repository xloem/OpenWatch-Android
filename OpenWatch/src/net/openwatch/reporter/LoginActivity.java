package net.openwatch.reporter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.Gson;
import com.loopj.android.http.*;

import net.openwatch.reporter.constants.Constants;
import net.openwatch.reporter.http.HttpClient;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Activity which displays a login screen to the user, offering registration as
 * well.
 */
public class LoginActivity extends Activity {
	
	private static final String TAG = "LoginActivity";
	
	AsyncHttpClient http_client;

	// Values for email and password at the time of the login attempt.
	private String mEmail;
	private String mPassword;

	// UI references.
	private EditText mEmailView;
	private EditText mPasswordView;
	private View mLoginFormView;
	private View mLoginStatusView;
	private TextView mLoginStatusMessageView;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		setContentView(R.layout.activity_login);

		// Set up the login form.
		mEmail = getIntent().getStringExtra(Constants.EMAIL);
		mEmailView = (EditText) findViewById(R.id.email);
		mEmailView.setText(mEmail);

		mPasswordView = (EditText) findViewById(R.id.password);
		mPasswordView
				.setOnEditorActionListener(new TextView.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView textView, int id,
							KeyEvent keyEvent) {
						if (id == R.id.login || id == EditorInfo.IME_NULL) {
							attemptLogin();
							return true;
						}
						return false;
					}
				});

		mLoginFormView = findViewById(R.id.login_form);
		mLoginStatusView = findViewById(R.id.login_status);
		mLoginStatusMessageView = (TextView) findViewById(R.id.login_status_message);

		findViewById(R.id.sign_in_button).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						attemptLogin();
					}
				});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.activity_login, menu);
		return true;
	}

	/**
	 * Attempts to sign in or register the account specified by the login form.
	 * If there are form errors (invalid email, missing fields, etc.), the
	 * errors are presented and no actual login attempt is made.
	 */
	public void attemptLogin() {
		if (http_client != null) {
			Log.d(TAG, "http_client is not null");
			return;
		}

		// Reset errors.
		mEmailView.setError(null);
		mPasswordView.setError(null);

		// Store values at the time of the login attempt.
		mEmail = mEmailView.getText().toString().trim();
		mPassword = mPasswordView.getText().toString();

		boolean cancel = false;
		View focusView = null;

		// Check for a valid password.
		if (TextUtils.isEmpty(mPassword)) {
			mPasswordView.setError(getString(R.string.error_field_required));
			focusView = mPasswordView;
			cancel = true;
		} else if (mPassword.length() < 4) {
			mPasswordView.setError(getString(R.string.error_invalid_password));
			focusView = mPasswordView;
			cancel = true;
		}

		// Check for a valid email address.
		if (TextUtils.isEmpty(mEmail)) {
			mEmailView.setError(getString(R.string.error_field_required));
			focusView = mEmailView;
			cancel = true;
		} else if (!checkEmail(mEmail)) {
			mEmailView.setError(getString(R.string.error_invalid_email));
			focusView = mEmailView;
			cancel = true;
		}

		if (cancel) {
			// There was an error; don't attempt login and focus the first
			// form field with an error.
			focusView.requestFocus();
		} else {
			// Show a progress spinner, and kick off a background task to
			// perform the user login attempt.
			mLoginStatusMessageView.setText(R.string.login_progress_signing_in);
			showProgress(true);
			UserLogin();
		}
	}

	/**
	 * Shows the progress UI and hides the login form.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	private void showProgress(final boolean show) {
		// On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
		// for very easy animations. If available, use these APIs to fade-in
		// the progress spinner.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			int shortAnimTime = getResources().getInteger(
					android.R.integer.config_shortAnimTime);

			mLoginStatusView.setVisibility(View.VISIBLE);
			mLoginStatusView.animate().setDuration(shortAnimTime)
					.alpha(show ? 1 : 0)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							mLoginStatusView.setVisibility(show ? View.VISIBLE
									: View.GONE);
						}
					});

			mLoginFormView.setVisibility(View.VISIBLE);
			mLoginFormView.animate().setDuration(shortAnimTime)
					.alpha(show ? 0 : 1)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							mLoginFormView.setVisibility(show ? View.GONE
									: View.VISIBLE);
						}
					});
		} else {
			// The ViewPropertyAnimator APIs are not available, so simply show
			// and hide the relevant UI components.
			mLoginStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
			mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
		}
	}
	
    private boolean checkEmail(String email) {
        return Constants.EMAIL_ADDRESS_PATTERN.matcher(email).matches();
    }
    
    /**
     * Login an existing account with the OpenWatch service
     * assuming mEmail and mPassword are pre-populated from the EditText fields
     */
    public void UserLogin(){
    	http_client = HttpClient.setupHttpClient(this);
    	Log.i(TAG,"Commencing login to: " + Constants.OW_URL + Constants.OW_LOGIN);
    	http_client.post(null, Constants.OW_URL + Constants.OW_LOGIN, getAuthJSON(), "application/json", new JsonHttpResponseHandler(){
    		
    		@Override
    		public void onStart(){
    			Log.i(TAG, "onStart");
    		}

    		@Override
    		public void onSuccess(JSONObject response){
    			Log.i(TAG,"OW login success: " +  response.toString());
    			try{
	    			if( (Boolean)response.getBoolean(Constants.OW_SUCCESS) == true){
	    				Log.i(TAG,"OW login success: " +  response.toString());
	    				// Set authed preference 
	    				setUserAuthenticated(response);
	    		        
	    		        Intent i = new Intent(LoginActivity.this, MainActivity.class);
	    		        startActivity(i);
	    		        return;
	    			} else{
	    				AlertDialog.Builder dialog = new AlertDialog.Builder(LoginActivity.this);
	    				int error_code = response.getInt(Constants.OW_ERROR);
	    				switch(error_code){
	    					
	    					case 403: 	// No account with provided email
	    						dialog.setTitle(R.string.login_dialog_unknown_email_title)
	    						.setMessage(getString(R.string.login_dialog_unknown_email_msg) + " " + mEmail + "?")
	    						.setNegativeButton(R.string.login_dialog_signup, signupDialogOnClickListener)
	    						.setPositiveButton(R.string.login_dialog_no, defaultDialogOnClickListener)
	    	    				.show();
	    						break;
	    					default:   // Incorrect email address / password (Error 412)
	    						dialog.setTitle(R.string.login_dialog_denied_title)
	    						.setMessage(R.string.login_dialog_denied_msg)
	    						.setNeutralButton(R.string.login_dialog_ok, defaultDialogOnClickListener)
	    	    				.show();
	    						break;
	       				}
	    					
	    			}
    			}catch (JSONException e){
    				Log.e(TAG, "error parsing json response");
    			}

    		}
    		
    		@Override
    	     public void onFailure(Throwable e, String response) {
    			Log.i(TAG,"OW login failure: " +  response);
    			AlertDialog.Builder dialog = new AlertDialog.Builder(LoginActivity.this);
    			dialog.setTitle(R.string.login_dialog_failed_title)
    			.setMessage(R.string.login_dialog_failed_msg)
    			.setNeutralButton(R.string.login_dialog_ok, defaultDialogOnClickListener)
    			.show();
    	     }
    		
    		@Override
    	     public void onFinish() {
    			Log.i(TAG,"OW login finish");
    			http_client = null;
    			showProgress(false);
    	     }
    	});
    	
    }
    
    /**
     * Create a new account with the OpenWatch servicee
     * assuming mEmail and mPassword are pre-populated from the EditText fields
     */
    public void UserSignup(){
    	http_client = HttpClient.setupHttpClient(this);
    	Log.i(TAG,"Commencing signup to: " + Constants.OW_URL + Constants.OW_SIGNUP);
    	http_client.post(null, Constants.OW_URL + Constants.OW_SIGNUP, getAuthJSON(), "application/json", new JsonHttpResponseHandler(){
    		
    		@Override
    		public void onSuccess(JSONObject response){
    			Log.i(TAG,"OW signup success: " +  response);
    			try{
    			if(response.getBoolean(Constants.OW_SUCCESS) == true){
    				Log.i(TAG,"OW signup success: " +  response.toString());
    				// Set authed preference 
    				setUserAuthenticated(response);
    		        
    		        Intent i = new Intent(LoginActivity.this, MainActivity.class);
    		        startActivity(i);
    		        return;
    			} else{
    				AlertDialog.Builder dialog = new AlertDialog.Builder(LoginActivity.this);
    				int error_code =  response.getInt(Constants.OW_ERROR);
    				switch(error_code){
    					
    					case 405: 	// email address in use
    						dialog.setTitle(R.string.signup_dialog_email_taken_title)
    						.setMessage(mEmail + " " +  getString(R.string.signup_dialog_email_taken_msg));
    						break;
    					default:
    						dialog.setTitle(R.string.signup_dialog_failed_title)
    						.setMessage(R.string.signup_dialog_failed_msg);
    						break;
       				}
    				dialog.setNeutralButton(R.string.login_dialog_ok, defaultDialogOnClickListener);
    				dialog.show();	
    			}
    			} catch(JSONException e){
    				Log.e(TAG, "Error parsing signup JSON");
    			}

    		}
    		
    		@Override
    	     public void onFailure(Throwable e, String response) {
    			Log.i(TAG,"OW signup failure: " +  response);
    			AlertDialog.Builder dialog = new AlertDialog.Builder(LoginActivity.this);
    			dialog.setTitle(R.string.login_dialog_failed_title)
    			.setMessage(R.string.login_dialog_failed_msg)
    			.setNeutralButton(R.string.login_dialog_ok, defaultDialogOnClickListener)
    			.show();
    	     }
    		
    		@Override
    	     public void onFinish() {
    			Log.i(TAG,"OW login finish");
    			http_client = null;
    	     }
    	});
    	
    }
    /**
     * Registers this mobile app with the OpenWatch service
     * sends the application version number
     */
    public void RegisterApp(String public_upload_token){
    	PackageInfo pInfo;
    	String app_version = "Android-";
		try {
			pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			app_version += pInfo.versionName;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Unable to read PackageName in RegisterApp");
			e.printStackTrace();
			app_version += "unknown";
		}
		
		HashMap<String,String> params = new HashMap<String, String>();
    	params.put(Constants.PUB_TOKEN, public_upload_token);
    	params.put(Constants.OW_SIGNUP_TYPE, app_version);
    	Gson gson = new Gson();
    	StringEntity se = null;
    	try {
			se = new StringEntity(gson.toJson(params));
		} catch (UnsupportedEncodingException e1) {
			Log.e(TAG,"Failed to put JSON string in StringEntity");
			e1.printStackTrace();
			return;
		}
		
		// Post public_upload_token, signup_type
		http_client = HttpClient.setupHttpClient(this);
    	Log.i(TAG,"Commencing ap registration to: " + Constants.OW_URL + Constants.OW_REGISTER + " pub_token: " + public_upload_token + " version: " + app_version);
    	http_client.post(null, Constants.OW_URL + Constants.OW_REGISTER, se, "application/json", new JsonHttpResponseHandler(){
    		@Override
    		public void onSuccess(JSONObject response){
    			Log.i(TAG,"OW app register success: " +  response);
    			
    			try{
	    			if( response.getBoolean(Constants.OW_SUCCESS) == true){
	    				Log.i(TAG,"OW app registration success: " +  response.toString());
	    				
	    				setRegisteredTask task = (setRegisteredTask) new setRegisteredTask().execute();
	    		        
	    		        Intent i = new Intent(LoginActivity.this, MainActivity.class);
	    		        startActivity(i);
	    		        return;
	    			} else{
	    				int error_code =  response.getInt(Constants.OW_ERROR);
	    				switch(error_code){
	    					
	    					case 415: 	// invalid public upload token
	    						Log.e(TAG, "invalid public upload token on app registration");
	    						break;
	    					default:
	    						Log.e(TAG, "Other error on app registration: " + response.getString(Constants.OW_REASON));
	    						break;
	       				}
	    			}
    			} catch (JSONException e){
    				Log.e(TAG, "Error parsing json registration response");
    			}
    		}
    		
    		@Override
    	     public void onFailure(Throwable e, String response) {
    			Log.i(TAG,"OW app registration failure: " +  response);	
    	     }
    		
    		@Override
    	     public void onFinish() {
    			Log.i(TAG,"OW app registration finish");
    			http_client = null;
    	     }
    	});
		
    	
    }
    
    public void setUserAuthenticated(JSONObject server_response){
    	SharedPreferences profile = getSharedPreferences(Constants.PROFILE_PREFS, MODE_PRIVATE);
    	if(!profile.getBoolean(Constants.REGISTERED, false))
			try {
				RegisterApp(server_response.getString(Constants.PUB_TOKEN));
			} catch (JSONException e) {
				Log.e(TAG, "Error reading pub token from JSON");
				e.printStackTrace();
			} 
    	// attempt registration on every login until it succeeds. 
    	new SavePreferencesTask().execute(server_response);
    	
    }
    
    /**
     * Save the OpenWatch service login response data to SharedPreferences
     * this includes the public and private upload token.
     * @author davidbrodsky
     *
     */
    private class SavePreferencesTask extends AsyncTask<JSONObject, Void, Void> {
        protected Void doInBackground(JSONObject... server_response_array) {
        	JSONObject server_response = server_response_array[0];
        	// Confirm returned email matches  
        	try {
				if( (server_response.getString(Constants.OW_EMAIL)).compareTo(mEmail) != 0)
					Log.e(TAG, "Email mismatch. Client submitted " + mEmail + " Server responded: " + ((String)server_response.get(Constants.OW_EMAIL)));
			
	        	SharedPreferences profile = getSharedPreferences(Constants.PROFILE_PREFS, MODE_PRIVATE);
	            SharedPreferences.Editor editor = profile.edit();
	            editor.putBoolean(Constants.AUTHENTICATED, true);
	            editor.putString(Constants.PUB_TOKEN, server_response.getString(Constants.PUB_TOKEN));
	            editor.putString(Constants.PRIV_TOKEN, server_response.getString(Constants.PRIV_TOKEN));
	            Log.i(TAG, "Got upload tokens. Pub: " +  server_response.getString(Constants.PUB_TOKEN) + " Priv: " + server_response.getString(Constants.PRIV_TOKEN));
	            editor.commit();
        	} catch (JSONException e) {
        		Log.e(TAG, "SavePreferenceTask: Error reading JSONObject response");
				e.printStackTrace();
			}
        	return null;
        }

        protected Void onPostExecute() {
        	showProgress(false);
        	return null;
        }
    }
    
    /**
     * Set the SharedPreferences to reflect app registration complete
     * @author davidbrodsky
     *
     */
    private class setRegisteredTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... server_response_array) {
        	SharedPreferences profile = getSharedPreferences(Constants.PROFILE_PREFS, MODE_PRIVATE);
            SharedPreferences.Editor editor = profile.edit();
            editor.putBoolean(Constants.REGISTERED, true);
            editor.commit();
        	return null;
        }

        protected Void onPostExecute() {
        	return null;
        }
    }
    
    public StringEntity getAuthJSON(){
    	HashMap<String,String> params = new HashMap<String, String>();
    	params.put(Constants.OW_EMAIL, mEmail);
    	params.put(Constants.OW_PW, mPassword);
    	Gson gson = new Gson();
    	StringEntity se = null;
    	try {
			se = new StringEntity(gson.toJson(params));
		} catch (UnsupportedEncodingException e1) {
			Log.e(TAG,"Failed to put JSON string in StringEntity");
			e1.printStackTrace();
		}
    	return se;
    }
    
    public OnClickListener defaultDialogOnClickListener = new OnClickListener(){

		@Override
		public void onClick(DialogInterface dialog, int which) {
			dialog.dismiss();
		}
    	
    };
    
    public OnClickListener signupDialogOnClickListener = new OnClickListener(){

		@Override
		public void onClick(DialogInterface dialog, int which) {
			showProgress(true);
			UserSignup();
		}
    	
    };

}
