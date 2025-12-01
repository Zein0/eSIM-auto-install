package android.service.euicc;

oneway interface IGetActivationCodeCallback {
    oneway void onSuccess(String activationCode);
    oneway void onFailure();
}