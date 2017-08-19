package com.kindelbit.grpcclient;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.security.ProviderInstaller;
import com.kindelbit.grpc.EmployeeServiceGrpc;
import com.kindelbit.grpc.Messages;
import com.squareup.okhttp.ConnectionSpec;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Iterator;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import io.grpc.ManagedChannel;
import io.grpc.okhttp.OkHttpChannelBuilder;

public class MainActivity extends AppCompatActivity {

   private static final String TAG = MainActivity.class.getSimpleName();
   private ManagedChannel mChannel;
   private String HOST = "10.0.0.82";
   private int PORT = 9000;
   private SSLSocketFactory sslSocketFactory;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);

      ProviderInstaller.installIfNeededAsync(this, providerInstallListener);
      init();
   }

   private void init() {
      try {

         mChannel = getChannel();

//         mChannel = ManagedChannelBuilder
//            .forAddress(HOST, PORT)
//            .usePlaintext(true)
//            .build();

         Log.i(TAG, "Channel opened.");
      } catch (Exception e) {
         e.printStackTrace();
      }

   }

   private ManagedChannel getChannel() throws Exception {

      SSLContext context = createContext();
      return OkHttpChannelBuilder
         .forAddress(HOST, PORT)
         .overrideAuthority("localhost")
         .connectionSpec(ConnectionSpec.MODERN_TLS)
         .sslSocketFactory(context.getSocketFactory())
         .build();
   }

   private SSLContext createContext() throws Exception {
      KeyStore trust_store = KeyStore.getInstance(KeyStore.getDefaultType());
      trust_store.load(null);

      InputStream inputStream = getAssets().open("cert.pem");

      CertificateFactory cert_factory = CertificateFactory.getInstance("X.509");
      Certificate cert = cert_factory.generateCertificate(inputStream);
      trust_store.setCertificateEntry("cert", cert);

      TrustManagerFactory trust_manager_factory = TrustManagerFactory.getInstance(
         TrustManagerFactory.getDefaultAlgorithm());
      trust_manager_factory.init(trust_store);
      TrustManager[] trust_manager = trust_manager_factory.getTrustManagers();

      SSLContext tlsContext = SSLContext.getInstance("TLSv1.2");
      tlsContext.init(null, trust_manager, null);

      return tlsContext;
   }

   @Override
   protected void onPause() {
      super.onPause();
      Log.i(TAG, "Closing Channel.");
      mChannel.shutdown();
   }

   ProviderInstaller.ProviderInstallListener providerInstallListener =
      new ProviderInstaller.ProviderInstallListener() {

         @Override
         public void onProviderInstalled() {
            Toast.makeText(MainActivity.this, "Install Success", Toast.LENGTH_SHORT).show();
         }

         @Override
         public void onProviderInstallFailed(int i, Intent intent) {
            Toast.makeText(MainActivity.this, "Install Error", Toast.LENGTH_SHORT).show();
         }
      };

   public void getEmployees(View view) {
      Toast.makeText(this, "Getting All Employees", Toast.LENGTH_SHORT).show();
      new GetAllEmployeeTask(new GetAllEmployeeRunnable()).execute();
   }

   private class GetAllEmployeeTask extends AsyncTask<Void, Void, Void> {

      private GrpcRunnable mGrpc;

      public GetAllEmployeeTask(GrpcRunnable grpcRunnable) {
         this.mGrpc = grpcRunnable;
      }

      @Override
      protected Void doInBackground(Void... voids) {
         try {
            String logs = mGrpc.run(EmployeeServiceGrpc.newBlockingStub(mChannel),
               EmployeeServiceGrpc.newStub(mChannel));
            Log.i(TAG, logs);
         } catch (Exception e) {
            Log.e(TAG, "Error", e);
         }
         return null;
      }
   }

   private interface GrpcRunnable {
      /**
       * Perform a grpc and return all the logs.
       */
      String run(EmployeeServiceGrpc.EmployeeServiceBlockingStub blockingStub,
                 EmployeeServiceGrpc.EmployeeServiceStub asyncStub) throws Exception;
   }

   private class GetAllEmployeeRunnable implements GrpcRunnable {

      @Override
      public String run(EmployeeServiceGrpc.EmployeeServiceBlockingStub blockingStub,
                        EmployeeServiceGrpc.EmployeeServiceStub asyncStub) throws Exception {
         Iterator<Messages.EmployeeResponse> all = blockingStub
            .getAll(Messages.GetAllRequest.newBuilder().build());

         int count = 0;
         while (all.hasNext()) {
            count++;
            Log.i(TAG, all.next().getEmployee().toString());
         }
         return "Got " + count + " employees";
      }
   }
}
