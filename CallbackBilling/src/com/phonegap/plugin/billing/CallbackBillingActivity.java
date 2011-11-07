package com.phonegap.plugin.billing;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.Log;

import com.phonegap.DroidGap;
import com.phonegap.api.PluginResult;
import com.phonegap.plugin.billing.plugin.BillingService;
import com.phonegap.plugin.billing.plugin.CallbackBillingPlugin;
import com.phonegap.plugin.billing.plugin.Consts;
import com.phonegap.plugin.billing.plugin.PurchaseDatabase;
import com.phonegap.plugin.billing.plugin.PurchaseObserver;
import com.phonegap.plugin.billing.plugin.ResponseHandler;
import com.phonegap.plugin.billing.plugin.BillingService.RequestPurchase;
import com.phonegap.plugin.billing.plugin.BillingService.RestoreTransactions;
import com.phonegap.plugin.billing.plugin.Consts.PurchaseState;
import com.phonegap.plugin.billing.plugin.Consts.ResponseCode;

public class CallbackBillingActivity extends DroidGap {
	public static final String TAG = "CallbackBillingActivity";
	
	public static CallbackBillingActivity eInstance = null;
	
	// Variable for Billing
    private CallbackBillingPlugin _pluginReference = null;
    
    /**
     * The SharedPreferences key for recording whether we initialized the
     * database.  If false, then we perform a RestoreTransactions request
     * to get all the purchases for this user.
     */
    private static final String DB_INITIALIZED = "db_initialized";

    // Flag indicate that app support in-app-billing or not
    private boolean isBillingSupported = false;
    
    private CallbackPurchaseObserver mCallbackPurchaseObserver;
    private Handler mHandler;

    private BillingService mBillingService;
    private PurchaseDatabase mPurchaseDatabase;
    private Cursor mOwnedItemsCursor;
    private Set<String> mOwnedItems = new HashSet<String>();

    /**
     * The developer payload that is sent with subsequent
     * purchase requests.
     */
    private String mPayloadContents = null;

    private static final int DIALOG_CANNOT_CONNECT_ID = 1;
    private static final int DIALOG_BILLING_NOT_SUPPORTED_ID = 2;

    /**
     * Each product in the catalog is either MANAGED or UNMANAGED.  MANAGED
     * means that the product can be purchased only once per user (such as a new
     * level in a game). The purchase is remembered by Android Market and
     * can be restored if this application is uninstalled and then
     * re-installed. UNMANAGED is used for products that can be used up and
     * purchased multiple times (such as poker chips). It is up to the
     * application to keep track of UNMANAGED products for the user.
     */
    private enum Managed { MANAGED, UNMANAGED }

    /**
     * A {@link PurchaseObserver} is used to get callbacks when Android Market sends
     * messages to this application so that we can update the UI.
     */
    private class CallbackPurchaseObserver extends PurchaseObserver {
        public CallbackPurchaseObserver(Handler handler) {
            super(CallbackBillingActivity.this, handler);
        }

        @Override
        public void onBillingSupported(boolean supported) {
            if (Consts.DEBUG) {
                Log.i(TAG, "supported: " + supported);
            }
            if (supported) {
            	// save a flag here to indicate that this app support in app billing
            	isBillingSupported = true;
            	//restoreDatabase();
            } else {
            	Log.d(TAG, "In App Billing not supported");
                //showDialog(DIALOG_BILLING_NOT_SUPPORTED_ID);
            }
        }

        @Override
        public void onPurchaseStateChange(PurchaseState purchaseState, String itemId,
                int quantity, long purchaseTime, String developerPayload) {
        	fireJavaScriptEvent("onPurchaseStateChange", "");

        	try {
        		JSONObject oResult = new JSONObject();
                oResult.put("event", "onPurchaseStateChange");
                
                if (Consts.DEBUG) {
                    Log.i(TAG, "onPurchaseStateChange() itemId: " + itemId + " " + purchaseState);
                }

                if (developerPayload == null) {
                    logProductActivity(itemId, purchaseState.toString());
                } else {
                    logProductActivity(itemId, purchaseState + "\n\t" + developerPayload);
                }

                if (purchaseState == PurchaseState.PURCHASED) {
                    mOwnedItems.add(itemId);
                    oResult.put("purchaseState", "PURCHASED");
                } else if (purchaseState == PurchaseState.CANCELED) {
                    oResult.put("purchaseState", "CANCELED");
                } else if (purchaseState == PurchaseState.REFUNDED) {
                    oResult.put("purchaseState", "REFUNDED");
                }
                //mCatalogAdapter.setOwnedItems(mOwnedItems);
                mOwnedItemsCursor.requery();
                
                // TODO: Send back the event to javascript
                if (_pluginReference != null) {
                    PluginResult result = new PluginResult(PluginResult.Status.OK, oResult.toString());
                    result.setKeepCallback(false);
                    _pluginReference.success(result, _pluginReference.getCallbackId());
                    _pluginReference.resetCallbackId();
                    _pluginReference = null;
                }
        	} catch (Exception e) {
				// TODO: handle exception
			}
        }

        @Override
        public void onRequestPurchaseResponse(RequestPurchase request,
                ResponseCode responseCode) {
        	fireJavaScriptEvent("onRequestPurchaseResponse", "");

        	try {
	            JSONObject oResult = new JSONObject();
                oResult.put("event", "onRequestPurchaseResponse");
                
	            if (Consts.DEBUG) {
	                Log.d(TAG, request.mProductId + ": " + responseCode);
	            }
	            if (responseCode == ResponseCode.RESULT_OK) {
	                oResult.put("responseCode", "RESULT_OK");
	                if (Consts.DEBUG) {
	                    Log.i(TAG, "purchase was successfully sent to server");
	                }
	                logProductActivity(request.mProductId, "sending purchase request");
	            } else if (responseCode == ResponseCode.RESULT_USER_CANCELED) {
	                oResult.put("responseCode", "RESULT_USER_CANCELED");
	                if (Consts.DEBUG) {
	                    Log.i(TAG, "user canceled purchase");
	                }
	                logProductActivity(request.mProductId, "dismissed purchase dialog");
	            } else {
	                oResult.put("responseCode", "RESULT_FAILED");
	                if (Consts.DEBUG) {
	                    Log.i(TAG, "purchase failed");
	                }
	                logProductActivity(request.mProductId, "request purchase returned " + responseCode);
	            }
	            // TODO: Send back the vent to javascript
	            oResult.put("productId", request.mProductId);
	            if (_pluginReference != null) {
	                PluginResult result = new PluginResult(PluginResult.Status.OK, oResult.toString());
	                result.setKeepCallback(true);
	                _pluginReference.success(result, _pluginReference.getCallbackId());    	
	            }
        	} catch (Exception e) {
				// TODO: handle exception
			}
        }

        @Override
        public void onRestoreTransactionsResponse(RestoreTransactions request,
                ResponseCode responseCode) {
        	//fireJavaScriptEvent("onRestoreTransactionsResponse", "");
        	
            if (responseCode == ResponseCode.RESULT_OK) {
                if (Consts.DEBUG) {
                    Log.d(TAG, "completed RestoreTransactions request");
                }
                // Update the shared preferences so that we don't perform
                // a RestoreTransactions again.
                SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor edit = prefs.edit();
                edit.putBoolean(DB_INITIALIZED, true);
                edit.commit();
            } else {
                if (Consts.DEBUG) {
                    Log.d(TAG, "RestoreTransactions error: " + responseCode);
                }
            }
        }
    }

    private String mItemName;
    private String mSku;
    //private CatalogAdapter mCatalogAdapter;
	// End of Variable for Billing
    
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        eInstance = this;
        
        mHandler = new Handler();
        mCallbackPurchaseObserver = new CallbackPurchaseObserver(mHandler);
        mBillingService = new BillingService();
        mBillingService.setContext(this);

        mPurchaseDatabase = new PurchaseDatabase(this);
        setupWidgets();

        // Check if billing is supported.
        ResponseHandler.register(mCallbackPurchaseObserver);
        if (!mBillingService.checkBillingSupported()) {
            showDialog(DIALOG_CANNOT_CONNECT_ID);
        }
        
        super.loadUrl("file:///android_asset/www/index.html");
    }
    
    /**
     * Called when this activity becomes visible.
     */
    @Override
    protected void onStart() {
        super.onStart();
        ResponseHandler.register(mCallbackPurchaseObserver);
        initializeOwnedItems();
    }

    /**
     * Called when this activity is no longer visible.
     */
    @Override
    protected void onStop() {
        super.onStop();
        ResponseHandler.unregister(mCallbackPurchaseObserver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPurchaseDatabase.close();
        mBillingService.unbind();
    }
    
    
//    @Override
//    protected Dialog onCreateDialog(int id) {
//        switch (id) {
//        case DIALOG_CANNOT_CONNECT_ID:
//            return createDialog(R.string.cannot_connect_title,
//                    R.string.cannot_connect_message);
//        case DIALOG_BILLING_NOT_SUPPORTED_ID:
//            return createDialog(R.string.billing_not_supported_title,
//                    R.string.billing_not_supported_message);
//        default:
//            return null;
//        }
//    }

//    private Dialog createDialog(int titleId, int messageId) {
//        String helpUrl = replaceLanguageAndRegion(getString(R.string.help_url));
//        if (Consts.DEBUG) {
//            Log.i(TAG, helpUrl);
//        }
//        final Uri helpUri = Uri.parse(helpUrl);
//
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle(titleId)
//            .setIcon(android.R.drawable.stat_sys_warning)
//            .setMessage(messageId)
//            .setCancelable(false)
//            .setPositiveButton(android.R.string.ok, null)
//            .setNegativeButton(R.string.learn_more, new DialogInterface.OnClickListener() {
//                public void onClick(DialogInterface dialog, int which) {
//                    Intent intent = new Intent(Intent.ACTION_VIEW, helpUri);
//                    startActivity(intent);
//                }
//            });
//        return builder.create();
//    }

    /**
     * Replaces the language and/or country of the device into the given string.
     * The pattern "%lang%" will be replaced by the device's language code and
     * the pattern "%region%" will be replaced with the device's country code.
     *
     * @param str the string to replace the language/country within
     * @return a string containing the local language and region codes
     */
    private String replaceLanguageAndRegion(String str) {
        // Substitute language and or region if present in string
        if (str.contains("%lang%") || str.contains("%region%")) {
            Locale locale = Locale.getDefault();
            str = str.replace("%lang%", locale.getLanguage().toLowerCase());
            str = str.replace("%region%", locale.getCountry().toLowerCase());
        }
        return str;
    }

    private void setupWidgets() {
//        mLogTextView = (TextView) findViewById(R.id.log);
//
//        mBuyButton = (Button) findViewById(R.id.buy_button);
//        mBuyButton.setEnabled(false);
//        mBuyButton.setOnClickListener(this);
//
//        mEditPayloadButton = (Button) findViewById(R.id.payload_edit_button);
//        mEditPayloadButton.setEnabled(false);
//        mEditPayloadButton.setOnClickListener(this);
//
//        mSelectItemSpinner = (Spinner) findViewById(R.id.item_choices);
//        mCatalogAdapter = new CatalogAdapter(this, CATALOG);
//        mSelectItemSpinner.setAdapter(mCatalogAdapter);
//        mSelectItemSpinner.setOnItemSelectedListener(this);
//
        mOwnedItemsCursor = mPurchaseDatabase.queryAllPurchasedItems();
        startManagingCursor(mOwnedItemsCursor);
        //String[] from = new String[] { PurchaseDatabase.PURCHASED_PRODUCT_ID_COL,
        //        PurchaseDatabase.PURCHASED_QUANTITY_COL
        //};
        //int[] to = new int[] { R.id.item_name, R.id.item_quantity };
        //mOwnedItemsAdapter = new SimpleCursorAdapter(this, R.layout.item_row,
//                mOwnedItemsCursor, from, to);
//        mOwnedItemsTable = (ListView) findViewById(R.id.owned_items);
//        mOwnedItemsTable.setAdapter(mOwnedItemsAdapter);
    }

    private void prependLogEntry(CharSequence cs) {
//        SpannableStringBuilder contents = new SpannableStringBuilder(cs);
//        contents.append('\n');
//        contents.append(mLogTextView.getText());
//        mLogTextView.setText(contents);
    }

    private void logProductActivity(String product, String activity) {
        SpannableStringBuilder contents = new SpannableStringBuilder();
        contents.append(Html.fromHtml("<b>" + product + "</b>: "));
        contents.append(activity);
        prependLogEntry(contents);
    }

    /**
     * If the database has not been initialized, we send a
     * RESTORE_TRANSACTIONS request to Android Market to get the list of purchased items
     * for this user. This happens if the application has just been installed
     * or the user wiped data. We do not want to do this on every startup, rather, we want to do
     * only when the database needs to be initialized.
     */
    private void restoreDatabase() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        boolean initialized = prefs.getBoolean(DB_INITIALIZED, false);
        if (!initialized) {
            mBillingService.restoreTransactions();
            //Toast.makeText(this, R.string.restoring_transactions, Toast.LENGTH_LONG).show();
            Log.d(TAG, "Restoring transactions");
        }
    }

    /**
     * Creates a background thread that reads the database and initializes the
     * set of owned items.
     */
    private void initializeOwnedItems() {
    	Log.d(TAG, "INITIALIZE OWNED ITEMS");
        new Thread(new Runnable() {
            public void run() {
                doInitializeOwnedItems();
            }
        }).start();
    }

    /**
     * Reads the set of purchased items from the database in a background thread
     * and then adds those items to the set of owned items in the main UI
     * thread.
     */
    private void doInitializeOwnedItems() {
        Cursor cursor = mPurchaseDatabase.queryAllPurchasedItems();
        if (cursor == null || cursor.getCount() == 0) {
        	Log.d(TAG, "NO OWNED Product found");
            return;
        }

        final Set<String> ownedItems = new HashSet<String>();
        try {
            int productIdCol = cursor.getColumnIndexOrThrow(
                    PurchaseDatabase.PURCHASED_PRODUCT_ID_COL);
            while (cursor.moveToNext()) {
                String productId = cursor.getString(productIdCol);
                Log.d(TAG, "Owned ProductId = " + productId);
                ownedItems.add(productId);
            }
        } finally {
            cursor.close();
        }

        // We will add the set of owned items in a new Runnable that runs on
        // the UI thread so that we don't need to synchronize access to
        // mOwnedItems.
        mHandler.post(new Runnable() {
            public void run() {
                mOwnedItems.addAll(ownedItems);
                //mCatalogAdapter.setOwnedItems(mOwnedItems);
            }
        });
    }

    public void test() {
    	fireJavaScriptEvent("test", "");
    }

    public void startRequestingPurchase(String productId, CallbackBillingPlugin plugin) {
    	_pluginReference = plugin;
    	
    	mSku = productId;
	    if (!mBillingService.requestPurchase(mSku, mPayloadContents)) {
	    	showDialog(DIALOG_BILLING_NOT_SUPPORTED_ID);
	    }
    }
    
    public void startRestoringDatabase() {
    	Log.d(TAG, "================Start restoring database===============");
    	restoreDatabase();
    }
    
    public JSONArray getPurchasedItems() {
    	Log.d(TAG, "getPurchasedItems");
    	if (mOwnedItemsCursor.requery() == false) {
    		Log.d(TAG, "Failed to requery");
    		return null;
    	}
    	if (mOwnedItemsCursor.moveToFirst() == false) {
    		Log.d(TAG, "Failed to move to first");
    		return null;
    	}

    	Log.d(TAG, "getPurchasedItems count = " + Integer.toString(mOwnedItemsCursor.getCount()));

    	try {
	    	JSONArray result = new JSONArray();
	    	while (!mOwnedItemsCursor.isAfterLast()) {
	    		JSONObject item = new JSONObject();
	    		Log.d(TAG, "columnCount = " + Integer.toString(mOwnedItemsCursor.getColumnCount()));
	    		for (int i = 0; i < mOwnedItemsCursor.getColumnCount(); i++) {
	    			Log.d(TAG,"columnName = " + mOwnedItemsCursor.getColumnName(i));
	        		if (mOwnedItemsCursor.getColumnName(i).equals(PurchaseDatabase.PURCHASED_PRODUCT_ID_COL)) {
	            		item.put("productId", mOwnedItemsCursor.getString(i));
	        		} else if (mOwnedItemsCursor.getColumnName(i).equals(PurchaseDatabase.PURCHASED_QUANTITY_COL)) {
	        			item.put("quantity", mOwnedItemsCursor.getInt(i));
	        		}
	    		}
	    		result.put(item);
	    		mOwnedItemsCursor.moveToNext();
	    	}
	    	
	    	return result;
    	} catch (Exception e) {
			// TODO: handle exception
    		e.printStackTrace();
    		return null;
		}
/*        mOwnedItemsCursor = mPurchaseDatabase.queryAllPurchasedItems();
        startManagingCursor(mOwnedItemsCursor);
        String[] from = new String[] { PurchaseDatabase.PURCHASED_PRODUCT_ID_COL,
                PurchaseDatabase.PURCHASED_QUANTITY_COL
        };
        if (mOwnedItemsCursor.getCount() > 0) {
        	Log.d(TAG, "Found OWNED ITEMS");
	        mOwnedItemsCursor.moveToFirst();
	        while (!mOwnedItemsCursor.isLast()) {
	        	String id = mOwnedItemsCursor.getString(0);
	        	Log.d(TAG, "id = " + id);
	        	mOwnedItemsCursor.moveToNext();
	        }
        } else {
        	Log.d(TAG, "[getPurchasedItems] NO OWN ITEMS FOUND");
        }*/
    }
    
    /**
     * Called when a button is pressed.
     */
//    public void onClick(View v) {
//        if (v == mBuyButton) {
//            if (Consts.DEBUG) {
//                Log.d(TAG, "buying: " + mItemName + " sku: " + mSku);
//            }
//            if (!mBillingService.requestPurchase(mSku, mPayloadContents)) {
//                showDialog(DIALOG_BILLING_NOT_SUPPORTED_ID);
//            }
//        } else if (v == mEditPayloadButton) {
//            showPayloadEditDialog();
//        }
//    }

    /**
     * Displays the dialog used to edit the payload dialog.
     */
//    private void showPayloadEditDialog() {
//        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
//        final View view = View.inflate(this, R.layout.edit_payload, null);
//        final TextView payloadText = (TextView) view.findViewById(R.id.payload_text);
//        if (mPayloadContents != null) {
//            payloadText.setText(mPayloadContents);
//        }
//
//        dialog.setView(view);
//        dialog.setPositiveButton(
//                R.string.edit_payload_accept,
//                new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int which) {
//                        mPayloadContents = payloadText.getText().toString();
//                    }
//                });
//        dialog.setNegativeButton(
//                R.string.edit_payload_clear,
//                new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int which) {
//                        if (dialog != null) {
//                            mPayloadContents = null;
//                            dialog.cancel();
//                        }
//                    }
//                });
//        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
//            public void onCancel(DialogInterface dialog) {
//                if (dialog != null) {
//                    dialog.cancel();
//                }
//            }
//        });
//        dialog.show();
//    }

    /**
     * Called when an item in the spinner is selected.
     */
//    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//        mItemName = getString(CATALOG[position].nameId);
//        mSku = CATALOG[position].sku;
//    }

//    public void onNothingSelected(AdapterView<?> arg0) {
//    }

    /**
     * An adapter used for displaying a catalog of products.  If a product is
     * managed by Android Market and already purchased, then it will be "grayed-out" in
     * the list and not selectable.
     */
//    private static class CatalogAdapter extends ArrayAdapter<String> {
//        private CatalogEntry[] mCatalog;
//        private Set<String> mOwnedItems = new HashSet<String>();
//
//        public CatalogAdapter(Context context, CatalogEntry[] catalog) {
//            super(context, android.R.layout.simple_spinner_item);
//            mCatalog = catalog;
//            for (CatalogEntry element : catalog) {
//                add(context.getString(element.nameId));
//            }
//            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//        }
//
//        public void setOwnedItems(Set<String> ownedItems) {
//            mOwnedItems = ownedItems;
//            notifyDataSetChanged();
//        }
//
//        @Override
//        public boolean areAllItemsEnabled() {
//            // Return false to have the adapter call isEnabled()
//            return false;
//        }
//
//        @Override
//        public boolean isEnabled(int position) {
//            // If the item at the given list position is not purchasable,
//            // then prevent the list item from being selected.
//            CatalogEntry entry = mCatalog[position];
//            if (entry.managed == Managed.MANAGED && mOwnedItems.contains(entry.sku)) {
//                return false;
//            }
//            return true;
//        }
//
//        @Override
//        public View getDropDownView(int position, View convertView, ViewGroup parent) {
//            // If the item at the given list position is not purchasable, then
//            // "gray out" the list item.
//            View view = super.getDropDownView(position, convertView, parent);
//            view.setEnabled(isEnabled(position));
//            return view;
//        }
//    }
 
    /**
     * Create an Event and dispatch it.
     * On Javascript side, there should be a listener, which is
     * listening for this event. Otherwise, the event will get lost
     */
	private void fireJavaScriptEvent(String event, String JSONstring) {
		Log.d(TAG, "[[[[[[[[["+ event +"]]]]]]]]]");
		/*this.loadUrl("javascript:" +
				"(function() {" +
				"var e = document.createEvent('Events');" +
				"e.initEvent('" + event + "');" +
				"e.result = '"+ JSONstring + "';" +
				"document.dispatchEvent(e);" +
			"})();");*/
		//this.loadUrl("javascript:(function() { "+event+"('"+ JSONstring.toString() +"');})()");
		//this.loadUrl("javascript:"+event+"("+ JSONstring.toString() +")");
	}    
}