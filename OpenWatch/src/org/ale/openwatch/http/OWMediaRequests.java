package org.ale.openwatch.http;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.google.gson.Gson;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.ale.openwatch.constants.Constants;
import org.ale.openwatch.model.OWLocalVideoRecording;
import org.ale.openwatch.model.OWLocalVideoRecordingSegment;
import org.ale.openwatch.model.OWVideoRecording;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class OWMediaRequests {

	private static final String TAG = "OWMediaServiceRequests";

    private static final String NULL_FILEPATH_ERROR = "error: Null Filepath";

	/**
	 * POSTs the start signal to the OW NodeMediaCapture Service
	 * 
	 * @param upload_token
	 *            the public upload token
	 * @param recording_id
	 *            recording id generated by client
	 * @param recording_start
	 *            when the recording started in unix time (seconds)
	 */
	public static void start(Context c, String upload_token, String recording_id,
			String recording_start) {
		AsyncHttpClient client = HttpClient.setupAsyncHttpClient(c);
		RequestParams params = new RequestParams();
		params.put(Constants.OW_REC_START, recording_start);
		String url = setupMediaURL(Constants.OW_MEDIA_START, upload_token,
				recording_id);
		Log.i(TAG, "sending start to " + url);
		client.post(url, params, new AsyncHttpResponseHandler() {

			@Override
			public void onSuccess(String response) {
				Gson gson = new Gson();
				Map<Object, Object> map = new HashMap<Object, Object>();
				try {
					map = gson.fromJson(response, map.getClass());
				} catch (Throwable t) {
					Log.e(TAG, "Error parsing response. 500 error?");
					onFailure(new Throwable(), "Error parsing server response");
					return;
				}

				if ((Boolean) map.get(Constants.OW_SUCCESS) == true) {
					Log.i(TAG, "start signal success: " + map.toString());
					return;
				} else {
					Log.i(TAG, "start signal server error: " + map.toString());
				}

			}

			@Override
			public void onFailure(Throwable e, String response) {
				Log.i(TAG, "start signal failure: " + response);
			}

			@Override
			public void onFinish() {
				Log.i(TAG, "start signal finished");
			}

		});
	}

	

	/**
	 * POSTs an end signal to the OW MediaCapture Service
	 * 
	 * @param upload_token
	 */
	public static void end(Context c, String upload_token,
			OWVideoRecording recording) {
		AsyncHttpClient client = HttpClient.setupAsyncHttpClient(c);
		String url = setupMediaURL(Constants.OW_MEDIA_END, upload_token,
				recording.uuid.get());
		Log.i(TAG, "sending end signal to " + url + " request body: "
				+ recording.local.get(c).toOWMediaServerJSON(c));
		client.post(c, url, Utils.JSONObjectToStringEntity(recording.local.get(c).toOWMediaServerJSON(c)), "application/json",
				new AsyncHttpResponseHandler() {

					@Override
					public void onSuccess(String response) {
						Gson gson = new Gson();
						Map<Object, Object> map = new HashMap<Object, Object>();
						try {
							map = gson.fromJson(response, map.getClass());
						} catch (Throwable t) {
							Log.e(TAG, "Error parsing response. 500 error?");
							onFailure(new Throwable(),
									"Error parsing server response");
							return;
						}

						if ((Boolean) map.get(Constants.OW_SUCCESS) == true) {
							Log.i(TAG, "end signal success: " + map.toString());
							return;
						} else {
							Log.i(TAG,
									"end signal server error: "
											+ map.toString());
						}

					}

					@Override
					public void onFailure(Throwable e, String response) {
						Log.i(TAG, "end signal failure: " + response);
						e.printStackTrace();
					}

				});
	}

	public static void updateMeta(Context c, String upload_token,
			OWVideoRecording recording) {
		AsyncHttpClient client = HttpClient.setupAsyncHttpClient(c);
        StringEntity params = Utils.JSONObjectToStringEntity(recording.toJsonObject(c));
		Log.i(TAG, "updateMeta: " + params.toString());
		String url = setupMediaURL(Constants.OW_MEDIA_UPDATE_META,
				upload_token, recording.uuid.get());
        client.post(c, url, params, "application/json", new AsyncHttpResponseHandler() {

			@Override
			public void onSuccess(String response) {
				Log.i(TAG, "got meta response " + response);

			}

			@Override
			public void onFailure(Throwable e, String response) {
				Log.i(TAG, "end signal failure: " + response);
				e.printStackTrace();
			}

		});
	}

    public static void safeSendHQFile(Context c, String upload_token,
                                      String recording_id, String filename, int model_id, OWServiceRequests.RequestCallback cb) {
        safeSendFile(
                c,
                setupMediaURL(Constants.OW_MEDIA_HQ_UPLOAD, upload_token,
                        recording_id), upload_token, recording_id, filename,
                true, model_id, cb);
    }

	public static void safeSendHQFile(Context c, String upload_token,
			String recording_id, String filename, int model_id) {
		safeSendFile(
				c,
				setupMediaURL(Constants.OW_MEDIA_HQ_UPLOAD, upload_token,
						recording_id), upload_token, recording_id, filename,
				true, model_id);
	}

	public static void safeSendLQFile(Context c, String upload_token,
			String recording_id, String filename, int segment_id) {
		safeSendFile(
				c,
				setupMediaURL(Constants.OW_MEDIA_UPLOAD, upload_token,
						recording_id), upload_token, recording_id, filename,
				false, segment_id);
	}

    private static void safeSendFile(final Context c, final String urlStr,
                                     String upload_token, String recording_id, final String filename,
                                     final boolean is_HQ, final int model_id) {
        safeSendFile(c, urlStr, upload_token, recording_id, filename, is_HQ, model_id, null);
    }

	/**
	 * Raw dog http post avoid reading entire file into memory
	 * 
	 * @param upload_token
	 * @param recording_id
	 * @param filename
	 */
	private static void safeSendFile(final Context c, final String urlStr,
			String upload_token, String recording_id, final String filename,
			final boolean is_HQ, final int model_id, final OWServiceRequests.RequestCallback cb) {
		new Thread() {

			@Override
			public void run() {
				try {
					String response_string = ApacheFilePost(c, urlStr, filename);
					Log.i(TAG, "urlHttpConnectionPost Response: " + response_string);
					if(response_string != null && !response_string.contains("error")){
						JSONObject response = new JSONObject(response_string);
						if (response != null
								&& response.has(Constants.OW_SUCCESS)
								&& response.getString(Constants.OW_SUCCESS)
										.compareTo("true") == 0) {
                            int server_object_id = -1;
							if (!is_HQ) {
								OWLocalVideoRecordingSegment segment = OWLocalVideoRecordingSegment
										.objects(c,
												OWLocalVideoRecordingSegment.class)
										.get(model_id);
								segment.uploaded.set(true);
								segment.save(c);
                                server_object_id = segment.local_recording.get(c).recording.get(c).media_object.get(c).getId();
							} else {
								OWLocalVideoRecording local = OWLocalVideoRecording
										.objects(c, OWLocalVideoRecording.class)
										.get(model_id);
								local.hq_synced.set(true);
                                server_object_id = local.recording.get(c).media_object.get(c).getId();
								if (local.areSegmentsSynced(c)) {
									local.lq_synced.set(true);
								}
								local.save(c);
                                Log.i("MediaSyncer", String.format("Set local recording %d hq_synced", local.getId()));
							}

                            // BEGIN OWServerObject Sync Broadcast
                            Log.d("sender", "Broadcasting message");
                            Intent intent = new Intent(Constants.OW_SYNC_STATE_FILTER);
                            // You can also include some extra data.
                            intent.putExtra(Constants.OW_SYNC_STATE_STATUS, 1);
                            intent.putExtra(Constants.OW_SYNC_STATE_MODEL_ID, server_object_id);
                            LocalBroadcastManager.getInstance(c).sendBroadcast(intent);
                            // END OWServerObject Sync Broadcast
                            if(cb != null)
                                cb.onSuccess();

						}
					}else if(response_string != null && response_string.compareTo(OWMediaRequests.NULL_FILEPATH_ERROR) == 0){
                        // if the request failed because the object has no media filepath set
                        OWLocalVideoRecording local = OWLocalVideoRecording
                                .objects(c, OWLocalVideoRecording.class)
                                .get(model_id);
                        local.setSynced(c, true); // set synced because this recording has been deleted from the device
                                                  // don't try to sync each app load
                    }else if(response_string != null && response_string.contains("error")){
                        if(cb != null)
                            cb.onFailure();
                    }
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ClientProtocolException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NullPointerException e){
                    e.printStackTrace();
                }
			}

		}.run();
	}
	
	public static String ApacheFilePost(Context c, String url, String filename, String post_key) throws ParseException, ClientProtocolException, IOException{
		final String TAG = "ApacheFilePost";
        if(filename == null)
            return NULL_FILEPATH_ERROR;
		Log.i(TAG, "url " + url + " filename " + filename);
		DefaultHttpClient client = HttpClient.setupDefaultHttpClient(c);
		HttpPost        post   = new HttpPost( url );
		
		if(HttpClient.USER_AGENT != null)
			post.setHeader("User-Agent", HttpClient.USER_AGENT);

		MultipartEntity entity = new MultipartEntity( HttpMultipartMode.BROWSER_COMPATIBLE );

		// For File parameters
		FileBody fileBody = new FileBody(new File(filename));
		entity.addPart(post_key, fileBody);

		post.setEntity( entity );

		// Here we go!
		String response = EntityUtils.toString( client.execute( post ).getEntity(), "UTF-8" );
		Log.i(TAG, "response: " + response);
		client.getConnectionManager().shutdown();
		
		return response;
	}
	
	/**
	 * I love Apache!
	 * @param url
	 * @param filename
	 * @return
	 * @throws ParseException
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public static String ApacheFilePost(Context c, String url, String filename) throws ParseException, ClientProtocolException, IOException{
		return ApacheFilePost(c, url, filename, "upload");
	}

	private static String setupMediaURL(String endpoint,
			String public_upload_token, String recording_id) {
		return Constants.OW_MEDIA_URL + endpoint + "/" + public_upload_token
				+ "/" + recording_id;
	}

}
