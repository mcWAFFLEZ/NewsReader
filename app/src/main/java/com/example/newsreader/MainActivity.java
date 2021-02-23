package com.example.newsreader;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private ListView myListView;
    private ArrayAdapter<String> myArrayAdapter;
    private ArrayList<String> titles = new ArrayList<>();
    private ArrayList<String> urls = new ArrayList<>();
    private SQLiteDatabase myDB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Load DataBase
        myDB = this.openOrCreateDatabase("Articles", MODE_PRIVATE, null);
        myDB.execSQL("CREATE TABLE IF NOT EXISTS articles (id INTEGER PRIMARY KEY, title VARCHAR, url VARCHAR)");

        //Start background thread.
        BackgroundTask task = new BackgroundTask();
        task.execute("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty");

        //Initiate all the vars needed for app.
        myListView = findViewById(R.id.myListView);
        myArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titles);
        myListView.setAdapter(myArrayAdapter);

        //Set new item click on ListView.
        myListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(getApplicationContext(), WebViewActivity.class);
                intent.putExtra("url",urls.get(position));
                startActivity(intent);
            }
        });

        updateListView();
    }

    public void updateListView(){

        Cursor c = myDB.rawQuery("SELECT * FROM articles",null);

        int titleIndex = c.getColumnIndex("title");
        int urlIndex = c.getColumnIndex("url");

        if (c.moveToFirst()) {
            titles.clear();
            urls.clear();

            do{

                titles.add(c.getString(titleIndex));
                urls.add(c.getString(urlIndex));
            }while(c.moveToNext());

            myArrayAdapter.notifyDataSetChanged();
        }
    }

    //Background thread task class.
    private class BackgroundTask extends AsyncTask<String, Void, String>{

        @Override
        protected String doInBackground(String... urls) {

            String result = "";
            URL url = null;
            HttpURLConnection connection = null;

            try {

                url = new URL(urls[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                InputStream in = connection.getInputStream();
                InputStreamReader reader = new InputStreamReader(in);
                int data = reader.read();

                while(data != -1){

                    char current = (char) data;
                    result += current;
                    data = reader.read();
                }

                //Log.i("ArticleIDs", result);

                JSONArray jsonArray = new JSONArray(result);

                int numberOfItems = 20;

                if(jsonArray.length() < 20){

                    numberOfItems = jsonArray.length();
                }

                myDB.execSQL("DELETE FROM articles");

                for(int i = 0; i < numberOfItems; i++){

                    String articleId = jsonArray.getString(i);
                    url = new URL("https://hacker-news.firebaseio.com/v0/item/"+articleId+".json?print=pretty");

                    connection = (HttpURLConnection) url.openConnection();
                    connection.connect();
                    in = connection.getInputStream();
                    reader = new InputStreamReader(in);
                    data = reader.read();

                    String articleInfo = "";

                    while(data != -1){

                        char current = (char) data;
                        articleInfo += current;
                        data = reader.read();
                    }

                    //Log.i("ArticleInfo", articleInfo);

                    JSONObject jsonObject = new JSONObject(articleInfo);

                    if(!jsonObject.isNull("title") && !jsonObject.isNull("url")){

                        String articleTitle = jsonObject.getString("title");
                        String articleUrl = jsonObject.getString("url");

                        Log.i("Title and Url", articleTitle +" "+ articleUrl);

                        String sql = "INSERT INTO articles (title, url) VALUES (?, ?)";
                        SQLiteStatement statement = myDB.compileStatement(sql);
                        statement.bindString(1,articleTitle);
                        statement.bindString(2,articleUrl);

                        statement.execute();
                    }
                }

                return result;

            } catch (Exception e){
                e.printStackTrace();
                return "Failed";
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            updateListView();
        }
    }
}
