/*!
 * MainActivity.java
 *
 * Copyright (c) 2020 ChikuwaTitan
 *
 * Released under the MIT license.
 * see https://opensource.org/licenses/MIT
 *
 */
package com.e.sasimplectrl;
import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.sip.SipSession;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcel;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
import android.media.MediaPlayer;
import android.widget.VideoView;
import eu.medsea.mimeutil.MimeUtil;

public class MainActivity extends Activity {
    private static final long   SCAN_PERIOD             = 10000;    // スキャン時間。単位はミリ秒。
    private static final int REQUEST_CODE_PICKER = 1;
    private static final int REQUEST_CODE_PERMISSION = 2;
    private Uri selectImageUri;
    private TextView mConsoleText;
    private ProgressDialog progressDialog;
    private static final int REQUEST_EXTERNAL_STORAGE = 1;


    Object sLockObj = new Object();


    // 定数

    // メンバー変数
    private String m_strInitialDir = Environment.getExternalStorageDirectory().getPath();    // 初期フォルダ
    // 定数
    private static final int MENUID_MEDIAFILE = 0;// ファイルメニューID
    private static final int MENUID_CYCCSVFILE = 1;// ファイルメニューID
    private static final int MENUID_UFOCSVFILE = 2;// ファイルメニューID
    private static final int    REQUEST_ENABLEBLUETOOTH = 3; // Bluetooth機能の有効化要求時の識別コード

    private static int CYC_SA = 0;
    private static int UFO_SA = 1;

    private static String CycSADeviceName = "CycSA";
    private static String UfoSADeviceName = "UFOSA";
    private static String CycSADeviceAddress;
    private static String UfoSADeviceAddress;
    private CsvReader sCycCsvData;
    private int sCycCsvRowIndex = 0;
    private CsvReader sUfoCsvData;
    private int sUfoCsvRowIndex = 0;
    private long sSystemTimer= System.currentTimeMillis();
    private  boolean sIsVideoFile;
    Timer sTimerTask;
    MediaController mediaController;

    Button sMediaResumeBtn;
    Button sCycScanBtn;
    Button sUfoScanBtn;
    Button sMediaStopBtn;
    SeekBar sMediaSeekBar;
    public Object m_obj;
    // メンバー変数
    private BluetoothAdapter mBluetoothAdapter;    // BluetoothAdapter : Bluetooth処理で必要
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothGatt mBleGattCyc;
    private BluetoothGatt mBleGattUfo;

    private BluetoothGattCharacteristic mBleCharacteristicCyc;
    private BluetoothGattCharacteristic mBleCharacteristicUfo;
    // 対象のサービスUUID.
    private static final String SERVICE_UUID = "40EE1111-63EC-4B7F-8CE7-712EFD55B90E";
    // キャラクタリスティックUUID.
    private static final String CHARACTERISTIC_UUID = "40EE2222-63EC-4B7F-8CE7-712EFD55B90E";
    public VideoView m_videoView;
    private Handler  mHandler;                            // UIスレッド操作ハンドラ : 「一定時間後にスキャンをやめる処理」で必要
    private String sScaningDevice;
    private static ScanSettings scanSettings;
    private static ArrayList scanFilterList = new ArrayList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );
        dispNormalScreen();

        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE}, 0);

        m_obj = this;
        // UIスレッド操作ハンドラの作成（「一定時間後にスキャンをやめる処理」で使用する）
        mHandler = new Handler();
        m_videoView = findViewById(R.id.videoView);

        // Android端末がBLEをサポートしてるかの確認
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_is_not_supported, Toast.LENGTH_SHORT).show();
            finish();    // アプリ終了宣言
            return;
        }

        // Bluetoothアダプタの取得
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (null == mBluetoothAdapter) {    // Android端末がBluetoothをサポートしていない
            Toast.makeText(this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT).show();
            finish();    // アプリ終了宣言
            return;
        }
        Button mediaBtn = findViewById(R.id.BTN_MediaFile);
        mediaBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.describeContents();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                String[] mimetypes = {"audio/*", "video/*"};
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);

                startActivityForResult(intent, MENUID_MEDIAFILE);
            }
        });

        Button cycCsvBtn = findViewById(R.id.BTN_CycCsvFile);
        cycCsvBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/comma-separated-values");


                startActivityForResult(intent, MENUID_CYCCSVFILE);
            }
        });
        Button ufoCsvBtn = findViewById(R.id.BTN_UfoCsvFile);
        ufoCsvBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/comma-separated-values");

                startActivityForResult(intent, MENUID_UFOCSVFILE);
            }
        });

        sCycScanBtn = findViewById(R.id.BTN_CycConnect);
        sCycScanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StartBLEScan(CycSADeviceName);
            }
        });

        sUfoScanBtn = findViewById(R.id.BTN_UfoConnect);
        sUfoScanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StartBLEScan(UfoSADeviceName);
            }
        });

        sMediaResumeBtn = findViewById(R.id.BTN_Resume);
        sMediaResumeBtn.setEnabled(false);
        sMediaResumeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(m_videoView.isPlaying()){
                    PauseProcess();
                }else{
                    ResumeProcess();
                }
            }
        });

        sMediaStopBtn = findViewById(R.id.BTN_Stop);
        sMediaStopBtn.setEnabled(false);
        sMediaStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StopProcess();
            }
        });

        sMediaSeekBar = findViewById(R.id.seekBar);
        sMediaSeekBar.setEnabled(false);
        sMediaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            Boolean pausing=false;
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if(!m_videoView.isPlaying()){
                    m_videoView.seekTo(i);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if(m_videoView.isPlaying()){
                    m_videoView.pause();
                    pausing = true;
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(pausing){
                    pausing = false;
                    CsvIndexSearch(seekBar.getProgress());
                    m_videoView.start();
                    ResumeMotorSend();
                }

            }
        });



        mediaController = new MediaController(this);

        mediaController.setPrevNextListeners(new View.OnClickListener() {
            public void onClick(View v) {

            }
        }, new View.OnClickListener() {
            public void onClick(View v) {

            }
        });

        m_videoView.setEnabled(false);

        m_videoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if(m_videoView.isPlaying()){
                    PauseProcess();
                }else{
                    ResumeProcess();
                }
                return false;
            }
        });

        m_videoView.setMediaController(mediaController);



        m_videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                /*再生終了痔の処理*/
                StopProcess();
            }
        });
        m_videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                sMediaSeekBar.setEnabled(true);
                sMediaSeekBar.setMax(m_videoView.getDuration());
                sMediaResumeBtn.setEnabled(true);
                sMediaStopBtn.setEnabled(true);
                m_videoView.setEnabled(true);
                sUfoCsvRowIndex=0;
                sCycCsvRowIndex=0;
                m_videoView.seekTo(0);

                final int topContainerId1 = getResources().getIdentifier("mediacontroller_progress", "id", "android");
                final SeekBar seekbar = (SeekBar) mediaController.findViewById(topContainerId1);
                seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    Boolean pausing=false;
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                        if(!m_videoView.isPlaying()){
                            m_videoView.seekTo(i);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        if(m_videoView.isPlaying()){
                            m_videoView.pause();
                            pausing = true;
                        }
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        if(pausing){
                            pausing = false;
                            CsvIndexSearch(seekBar.getProgress());
                            m_videoView.start();
                            ResumeMotorSend();
                        }

                    }
                });


            }
        });
    }

    private void ResumeMotorSend(){

        if(sCycCsvData != null) {
            if(sCycCsvRowIndex != 0){
                int csvIndex = sCycCsvRowIndex - 1 ;
                byte speed = sCycCsvData.csvData.get(csvIndex).speed;
                byte mode = sCycCsvData.csvData.get(csvIndex).rotate;
                SendMotorCmd(speed, mode, CYC_SA);
            }
        }

        if(sUfoCsvData != null){
            if(sUfoCsvRowIndex != 0){
                int csvIndex = sUfoCsvRowIndex -1;
                byte speed = sUfoCsvData.csvData.get(csvIndex).speed;
                byte mode = sUfoCsvData.csvData.get(csvIndex).rotate;
                SendMotorCmd(speed,mode,UFO_SA);
            }
        }
    }


    private  void StartBLEScan(String devName){

        // プログレスダイアログを表示する
        progressDialog = new ProgressDialog(this);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage("Searching...");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        sScaningDevice = devName;
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        ScanFilter scanFilter =
                new ScanFilter.Builder()
                        .setDeviceName(devName)
                        .build();


        scanFilterList.add(scanFilter);
        scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.MATCH_MODE_AGGRESSIVE).build();

        mHandler.postDelayed( new Runnable()
        {
            @Override
            public void run()
            {
                progressDialog.cancel();
                mBluetoothLeScanner.stopScan( mLeScanCallback );
            }
        }, SCAN_PERIOD );

        mBluetoothLeScanner.startScan(scanFilterList,scanSettings,mLeScanCallback);
    }

    // スキャンの停止
    private void stopScan()
    {
        // 一定期間後にスキャン停止するためのHandlerのRunnableの削除
        mHandler.removeCallbacksAndMessages( null );
        progressDialog.cancel();

        // BluetoothLeScannerの取得
        android.bluetooth.le.BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        if( null == scanner )
        {
            return;
        }
        scanner.stopScan( mLeScanCallback );
    }

    private void PauseProcess() {

        m_videoView.pause();
        sTimerTask.cancel();

        sTimerTask = null;

        try {
            TimeUnit.MILLISECONDS.sleep((long)100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        SendMotorCmd((byte) 0, (byte) 0, CYC_SA);
        SendMotorCmd((byte) 0, (byte) 0, UFO_SA);

        sMediaResumeBtn.setText("▶");
        LinearLayout firstRow = findViewById(R.id.SettingRow);
        firstRow.setVisibility(View.VISIBLE);
        dispNormalScreen();

    }
    private void StopProcess()
    {
        if(sTimerTask !=null){
            sTimerTask.cancel();
            sTimerTask = null;
        }

        SendMotorCmd((byte)0,(byte)0,CYC_SA);
        SendMotorCmd((byte)0,(byte)0,UFO_SA);
        m_videoView.pause();
        m_videoView.seekTo(0);
        sMediaSeekBar.setProgress(0);
        sMediaResumeBtn.setText("▶");
        LinearLayout firstRow = findViewById(R.id.SettingRow);
        firstRow.setVisibility(View.VISIBLE);
        dispNormalScreen();


        sCycCsvRowIndex = 0;
        sUfoCsvRowIndex = 0;

    }

    private void CsvIndexSearch(int postime){/*ms*/
        boolean searchEnd = true;
        if ((sCycCsvData != null)) {
            for(int i=0;i<sCycCsvData.csvData.size();i++) {
                if (sCycCsvData.csvData.get(i).time >= postime) {
                    sCycCsvRowIndex = i;
                    searchEnd = false;
                    break;
                }
            }
            if(searchEnd == true){
                sCycCsvRowIndex = sCycCsvData.csvData.size();
            }
        }

        searchEnd = true;
        if ((sUfoCsvData != null)) {
            for(int i=0;i<sUfoCsvData.csvData.size();i++) {
                if (sUfoCsvData.csvData.get(i).time >= postime) {
                    sUfoCsvRowIndex = i;
                    searchEnd = false;
                    break;
                }
            }
            if(searchEnd == true){
                sUfoCsvRowIndex = sUfoCsvData.csvData.size();
            }
        }
    }

    private void ResumeProcess()
    {
        LinearLayout otherRow = findViewById(R.id.SettingRow);
        if(sIsVideoFile){
            otherRow.setVisibility(View.GONE);
            dispFullScreen();
        }
        sMediaResumeBtn.setText("||");
        int currentPosition = m_videoView.getCurrentPosition();/*ms*/
        CsvIndexSearch(currentPosition);
        m_videoView.start();

        ResumeMotorSend();

        sTimerTask = new Timer();
        sTimerTask.schedule(new TimerTask() {
            @Override
            public void run() {
                int currentPosition = m_videoView.getCurrentPosition();/*ms*/
                sMediaSeekBar.setProgress(currentPosition);
                if ((sCycCsvData != null)) {
                    if (sCycCsvData.csvData.size() <= sCycCsvRowIndex) {
                        /*do nothing*/
                    } else {
                        if (sCycCsvData.csvData.get(sCycCsvRowIndex).time <= currentPosition) {
                            // クリティカルセッション
                            if(sTimerTask != null){
                                byte speed = sCycCsvData.csvData.get(sCycCsvRowIndex).speed;
                                byte mode = sCycCsvData.csvData.get(sCycCsvRowIndex).rotate;
                                SendMotorCmd(speed, mode, CYC_SA);
                            }
                            /*Indexを次の行に移す*/
                            sCycCsvRowIndex++;
                        }
                    }
                }

                if ((sUfoCsvData != null)) {
                    if (sUfoCsvData.csvData.size() <= sUfoCsvRowIndex) {
                        /*do nothing*/
                    } else {
                        if (sUfoCsvData.csvData.get(sUfoCsvRowIndex).time <= currentPosition) {
                                // クリティカルセッション
                            if(sTimerTask != null){
                                byte speed = sUfoCsvData.csvData.get(sUfoCsvRowIndex).speed;
                                byte mode = sUfoCsvData.csvData.get(sUfoCsvRowIndex).rotate;
                                SendMotorCmd(speed, mode, UFO_SA);
                            }

                            /*Indexを次の行に移す*/
                            sUfoCsvRowIndex++;
                        }
                    }
                }
            }
        }, 0, 100);

    }

    void SendMotorCmd(byte speed,byte mode, int device){
        byte motor;
        motor = (byte) ((mode << 7) & 0x80);
        motor = (byte) (motor | speed);
        if(device == CYC_SA){
            byte[] sendByte = {0x01, 0x01, motor};
            if (mBleGattCyc != null) {
                mBleCharacteristicCyc.setValue(sendByte);
                mBleGattCyc.writeCharacteristic(mBleCharacteristicCyc);
            }
        }
        else if(device == UFO_SA){
            byte[] sendByte = {0x02, 0x01, motor};
            if (mBleGattUfo != null) {
                mBleCharacteristicUfo.setValue(sendByte);
                mBleGattUfo.writeCharacteristic(mBleCharacteristicUfo);
            }
        }
    }
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            // 接続状況が変化したら実行.
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mBluetoothLeScanner.stopScan(mLeScanCallback);
                // 接続に成功したらサービスを検索する.
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // 接続が切れたらGATTを空にする.
                String devAddress = gatt.getDevice().getName();
                if(devAddress.equals(CycSADeviceName)){
                    if (mBleGattCyc != null) {
                        mBleGattCyc.close();
                        mBleGattCyc = null;
                    }
                    mHandler.post(new Runnable() {
                        //run()の中の処理はメインスレッドで動作されます。
                        public void run() {
                            sCycScanBtn.setEnabled(true);
                        }
                    });
                }else if(devAddress.equals(UfoSADeviceName)){
                    if (mBleGattUfo != null) {
                        mBleGattUfo.close();
                        mBleGattUfo = null;
                    }
                    mHandler.post(new Runnable() {
                        //run()の中の処理はメインスレッドで動作されます。
                        public void run() {
                            sUfoScanBtn.setEnabled(true);
                        }
                    });
                }else{
                    /**/
                }
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            // Serviceが見つかったら実行.
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // UUIDが同じかどうかを確認する.
                BluetoothGattService service = gatt.getService(UUID.fromString(SERVICE_UUID));
                if (service != null)
                {
                    if(sScaningDevice == CycSADeviceName){
                        // 指定したUUIDを持つCharacteristicを確認する.
                        mBleCharacteristicCyc = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
                        if (mBleCharacteristicCyc != null) {
                            // Service, CharacteristicのUUIDが同じならBluetoothGattを更新する.
                            mBleGattCyc = gatt;
                            sCycScanBtn.setEnabled(false);
                            stopScan();
                        }
                    }
                    else if(sScaningDevice == UfoSADeviceName){
                        mBleCharacteristicUfo = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
                        if (mBleCharacteristicUfo != null) {
                            // Service, CharacteristicのUUIDが同じならBluetoothGattを更新する.
                            mBleGattUfo = gatt;
                            sUfoScanBtn.setEnabled(false);
                            stopScan();
                        }
                    }
                }
            }
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
        {
            // キャラクタリスティックのUUIDをチェック(getUuidの結果が全て小文字で帰ってくるのでUpperCaseに変換)
            //if (CHARACTERISTIC_UUID.equals(characteristic.getUuid().toString().toUpperCase()))
            //{
            //}
        }

    };

    // デバイススキャンコールバック
    public ScanCallback mLeScanCallback = new ScanCallback()
    {

        // スキャンに成功（アドバタイジングは一定間隔で常に発行されているため、本関数は一定間隔で呼ばれ続ける）
        @Override
        public void onScanResult( int callbackType, final ScanResult result )
        {
            BluetoothDevice device= result.getDevice();
            device.connectGatt((Context) m_obj,false,mGattCallback);
        }

        // スキャンに失敗
        @Override
        public void onScanFailed( int errorCode )
        {
            super.onScanFailed( errorCode );
        }
    };


    public static String getSuffix(String fileName) {
        if (fileName == null) return null;
        int point = fileName.lastIndexOf(".");
        if (point != -1) {
            return fileName.substring(point + 1);
        }
        return null;
    }

    // 初回表示時、および、ポーズからの復帰時
    @Override
    protected void onResume()
    {
        super.onResume();

        // Android端末のBluetooth機能の有効化要求
        requestBluetoothFeature();
    }

    // Android端末のBluetooth機能の有効化要求
    private void requestBluetoothFeature()
    {
        if( mBluetoothAdapter.isEnabled() )
        {
            return;
        }
        // デバイスのBluetooth機能が有効になっていないときは、有効化要求（ダイアログ表示）
        Intent enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
        startActivityForResult( enableBtIntent, REQUEST_ENABLEBLUETOOTH );
    }

    // 機能の有効化ダイアログの操作結果
    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data )
    {
        switch( requestCode )
        {
            case REQUEST_ENABLEBLUETOOTH: // Bluetooth有効化要求
                if( Activity.RESULT_CANCELED == resultCode )
                {    // 有効にされなかった
                    Toast.makeText( this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT ).show();
                    finish();    // アプリ終了宣言
                    return;
                }
                break;
            case MENUID_MEDIAFILE:
                if(resultCode == Activity.RESULT_OK){
                    if (data.getData() != null) {
                        Uri uri = data.getData();
                        String fileName = getFileNameFromUri(this,uri);
                        TextView mediaPath =  findViewById(R.id.TB_MediaPath);

                        String fileExtension = getSuffix(fileName);
                        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
                        if(mimeType.contains("video")){
                            sIsVideoFile = true;
                        }else if(mimeType.contains(("audio")))
                        {
                            sIsVideoFile = false;
                        }else{
                            return ;
                        }

                        try{
                            m_videoView.setVideoURI(uri);
                            mediaPath.setText(fileName);
                        }catch(Exception e){
                            Toast ts = Toast.makeText(this, "再生できないメディアファイルが選択されました", Toast.LENGTH_LONG);
                            ts.show();
                        }
                    }
                }
                break;
            case MENUID_CYCCSVFILE:
                if(resultCode == Activity.RESULT_OK){
                    if (data.getData() != null) {
                        Uri uri = data.getData();
                        String fileName = getFileNameFromUri(this,uri);
                        TextView csvPathCyc =  findViewById(R.id.TB_CycCsvPath);
                        String fileExtension = getSuffix(fileName);
                        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());

                        if(fileExtension.contains("csv")){
                            try{
                                sCycCsvData = new CsvReader(this,uri);
                                csvPathCyc.setText(fileName);
                                sUfoCsvRowIndex=0;
                                sCycCsvRowIndex=0;
                                m_videoView.seekTo(0);
                            } catch (Exception e) {
                                Toast ts = Toast.makeText(this, "csvファイルのフォーマットが適切ではありません", Toast.LENGTH_LONG);
                                ts.show();
                            }
                        }
                    }else{
                        Toast ts = Toast.makeText(this, "csvファイルではありません", Toast.LENGTH_LONG);
                        ts.show();
                    }
                }
                break;
            case MENUID_UFOCSVFILE:
                if(resultCode == Activity.RESULT_OK){
                    if (data.getData() != null) {
                        Uri uri = data.getData();
                        String fileName = getFileNameFromUri(this,uri);
                        TextView csvPathUfo =  findViewById(R.id.TB_UfoCsvPath);
                        String fileExtension = getSuffix(fileName);
                        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());

                        if(fileExtension.contains("csv")){
                            try{
                                sUfoCsvData = new CsvReader(this,uri);
                                csvPathUfo.setText(fileName);
                                sUfoCsvRowIndex=0;
                                sCycCsvRowIndex=0;
                                m_videoView.seekTo(0);
                            } catch (Exception e) {
                                Toast ts = Toast.makeText(this, "csvファイルのフォーマットが適切ではありません", Toast.LENGTH_LONG);
                                ts.show();
                            }
                        }else{
                            Toast ts = Toast.makeText(this, "csvファイルではありません", Toast.LENGTH_LONG);
                            ts.show();
                        }
                    }
                }
                break;
            default:
                break;
        }
        super.onActivityResult( requestCode, resultCode, data );
    }

    public static String getFileNameFromUri( Context context, Uri uri) {
        // is null
        if (null == uri) {
            return null;
        }

        // get scheme
        String scheme = uri.getScheme();

        // get file name
        String fileName = null;
        switch (scheme) {
            case "content":
                String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
                Cursor cursor = context.getContentResolver()
                        .query(uri, projection, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        fileName = cursor.getString(
                                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME));
                    }
                    cursor.close();
                }
                break;

            case "file":
                fileName = new File(uri.getPath()).getName();
                break;

            default:
                break;
        }
        return fileName;
    }
    private  void dispFullScreen(){
        View decor = getWindow().getDecorView();
        // hide navigation bar, hide status bar
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }
    private  void dispNormalScreen(){
        View decor = getWindow().getDecorView();
        // hide navigation bar, hide status bar
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }
}
