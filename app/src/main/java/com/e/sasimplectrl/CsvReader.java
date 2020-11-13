/*!
 * CsvReader.java
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
import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.ArrayList;

public class CsvReader {

    public ArrayList<RowData> csvData;


    public class RowData{
        public int time;
        public byte rotate;
        public byte speed;
    }
    public CsvReader(Context context, Uri uri){
        try {
            // CSVファイルの読み込み
            InputStream stream = context.getContentResolver().openInputStream(uri);
            InputStreamReader inputStreamReader = new InputStreamReader(stream);
            BufferedReader bufferReader = new BufferedReader(inputStreamReader);
            //BufferedReader bufferReader = new BufferedReader(inputStreamReader);
            String line;
            csvData = new ArrayList<RowData>();

            /*最初に停止用の行を挿入*/
            RowData firstRow = new RowData();
            firstRow.time = 0;
            firstRow.rotate = 0;
            firstRow.speed =0;
            csvData.add(firstRow);

            while ((line = bufferReader.readLine()) != null) {

                //カンマ区切りで１つづつ配列に入れる
                String[] rowData = line.split(",");
                if(rowData.length >= 3){
                    RowData temp = new RowData();
                    temp.time = Integer.parseInt(rowData[0].replaceAll("[^0-9]", "")) * 100;
                    temp.rotate = Byte.parseByte(rowData[1].replaceAll("[^0-9]", ""));
                    temp.speed =Byte.parseByte(rowData[2].replaceAll("[^0-9]", ""));

                    csvData.add(temp);
                }
            }
            bufferReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
