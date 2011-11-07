function CallbackBillingPlugin() {
	
}

CallbackBillingPlugin.prototype.test = function(success, fail) {
	PhoneGap.exec(success, fail, "CallbackBillingPlugin", "test", []);
};

CallbackBillingPlugin.prototype.requestPurchase = function(success, fail, productId) {
	PhoneGap.exec(success, fail, "CallbackBillingPlugin", "requestPurchase", [productId]);
};

CallbackBillingPlugin.prototype.getPurchasedItems = function(success, fail) {
	console.log('CallbackBillingPlugin.prototype.getPurchasedItem');
	PhoneGap.exec(success, fail, "CallbackBillingPlugin", "getPurchasedItems", []);
};

CallbackBillingPlugin.prototype.restoreDatabase = function(success, fail) {
	console.log('CallbackBillingPlugin.prototype.restoreDatabase');
	PhoneGap.exec(success, fail, "CallbackBillingPlugin", "restoreDatabase", []);
};


/* function(transactionIdentifier, productId, transactionReceipt) */
CallbackBillingPlugin.prototype.onPurchaseStateChange = null;

/* function(originalTransactionIdentifier, productId, originalTransactionReceipt) */
CallbackBillingPlugin.prototype.onRequestPurchaseResponse = null;

/* function(errorCode, errorText) */
CallbackBillingPlugin.prototype.onRestoreTransactionsResponse = null;


CallbackBillingPlugin.prototype.updatedTransactionCallback = function(state, errorCode, errorText, transactionIdentifier, productId, transactionReceipt) {
	switch(state) {
		case "PaymentTransactionStatePurchased":
			if(this.onPurchaseStateChange) {
				this.onPurchaseStateChange(transactionIdentifier, productId, transactionReceipt);
			} else {
				this.eventQueue.push(arguments);
				this.watchQueue();
			}
			return; 

		case "PaymentTransactionStateFailed":
			if(this.onRequestPurchaseResponse) {
				this.onRequestPurchaseResponse(errorCode, errorText);
			} else {
				this.eventQueue.push(arguments);
				this.watchQueue();
			}
			return;

		case "PaymentTransactionStateRestored":
			if(this.onRestoreTransactionsResponse) {
				this.onRestoreTransactionsResponse(transactionIdentifier, productId, transactionReceipt);
			} else {
				this.eventQueue.push(arguments);
				this.watchQueue();
			}
			return;
	}
};

/*
 * This queue stuff is here because we may be sent events before listeners have been registered. This is because if we have 
 * incomplete transactions when we quit, the app will try to run these when we resume. If we don't register to receive these
 * right away then they may be missed. As soon as a callback has been registered then it will be sent any events waiting
 * in the queue.
 */

CallbackBillingPlugin.prototype.runQueue = function() {
	if(!this.eventQueue.length || (!this.onPurchaseStateChange && !this.onRequestPurchaseResponse && !this.onRestoreTransactionsResponse)) {
		return;
	}
	var args;
	/* We can't work directly on the queue, because we're pushing new elements onto it */
	var queue = this.eventQueue.slice();
	this.eventQueue = [];
	while (args = queue.shift()) {
		this.updatedTransactionCallback.apply(this, args);
	}
	if (!this.eventQueue.length) {	
		this.unWatchQueue();
	}
}

CallbackBillingPlugin.prototype.watchQueue = function() {
	if(this.timer) {
		return;
	}
	this.timer = setInterval("window.plugins.CallbackBillingPlugin.runQueue()", 10000);
}

CallbackBillingPlugin.prototype.unWatchQueue = function() {
	if(this.timer) {
		clearInterval(this.timer);
		this.timer = null;
	}
}

CallbackBillingPlugin.prototype.eventQueue = [];
CallbackBillingPlugin.prototype.timer = null;

PhoneGap.addConstructor(function() {
	PhoneGap.addPlugin("CallbackBillingPlugin", new CallbackBillingPlugin());
});