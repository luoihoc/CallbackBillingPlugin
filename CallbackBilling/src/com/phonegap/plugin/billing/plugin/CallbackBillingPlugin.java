package com.phonegap.plugin.billing.plugin;

import org.json.JSONArray;
import org.json.JSONObject;

import android.util.Log;
import android.webkit.WebSettings.PluginState;

import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;
import com.phonegap.plugin.billing.CallbackBillingActivity;

public class CallbackBillingPlugin extends Plugin {
	private static final String TAG = "CallbackBillingPlugin";

	private String _callbackId = null;

	@Override
	public PluginResult execute(String action, JSONArray args, String callbackId) {
		//Check the action that javascript has sent.
		Log.d(TAG, "CallbackBillingPlugin CALLED");
		try {
			if (action.equals("test")){
				Log.d(TAG, "TEST");
				CallbackBillingActivity.eInstance.test();
				
				return new PluginResult(PluginResult.Status.OK);
			} else if (action.equals("requestPurchase")) {
				String productId = args.getString(0);
				Log.d(TAG, "productId = " + productId);
					
				if (_callbackId != null) {
					Log.d(TAG, "Last purchase is not finished");
					return new PluginResult(PluginResult.Status.ERROR, "Please wait until the last purchase finishs");
				}

				// Save this for the callback
				this._callbackId = callbackId;
				
				return _requestPurchase(productId);
			} else if (action.equals("restoreDatabase")){
				Log.d(TAG, "restore database");
				_restoreDatabase();
				
				return new PluginResult(PluginResult.Status.OK);
			} else if (action.equals("getPurchasedItems")){
				Log.d(TAG, "get purchased items");
				return _getPurchasedItems();
			} else {
				return new PluginResult(PluginResult.Status.INVALID_ACTION);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return new PluginResult(PluginResult.Status.ERROR);
		}
	}

	public String getCallbackId() {
		return this._callbackId;
	}
	
	public void resetCallbackId() {
		this._callbackId = null;
	}

	private PluginResult _requestPurchase(String productId) {
		try {
			Log.d(TAG, "Request Purchase: " + productId);
			
			CallbackBillingActivity.eInstance.startRequestingPurchase(productId, this);

			PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
			pluginResult.setKeepCallback(true);
			return pluginResult;
		} catch (Exception e) {
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, "Failed to start the purchase process");
			return pluginResult;
		}
	}
	
	private void _restoreDatabase() {
		CallbackBillingActivity.eInstance.startRestoringDatabase();
	}

	private PluginResult _getPurchasedItems() {
		JSONObject result = new JSONObject();
		try {
			JSONArray items = CallbackBillingActivity.eInstance.getPurchasedItems();
			if (items != null) {
				result.put("status", "OK");
				result.put("result", items);
				return new PluginResult(PluginResult.Status.OK, result.toString());
			} else {
				result.put("status", "NO_RESULT");
				return new PluginResult(PluginResult.Status.OK, result.toString());
			}
		} catch (Exception e) {
			return new PluginResult(PluginResult.Status.ERROR, "Failed to get owned item list");
		}
	}
	
}
