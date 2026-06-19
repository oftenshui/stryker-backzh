package com.zalexdev.stryker.localnetwork.utils;

import android.annotation.SuppressLint;
import android.os.AsyncTask;

import com.zalexdev.stryker.utils.Core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;

public class CutNetwork extends AsyncTask<Void, Void, Void> {


    public Core core;
    public String target;
    public String gateway;
    public Process process;
    public int type;

    public CutNetwork(Core c, String t, String gw, int ty) {
        core = c;
        target = t;
        gateway = gw;
        type = ty;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

    }

    @SuppressLint("WrongThread")
    @Override
    protected Void doInBackground(Void... command) {
        String line;


        try {

            process = Runtime.getRuntime().exec("su -mm");
            OutputStream stdin = process.getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();
            if (type == 0) {
                core.getLogger().writeLine("Cutting network connection to " + target + " from " + gateway,1);
                stdin.write((Core.EXECUTE + " 'python3 /CORE/MegaCut/megacut.py " + target + " " + gateway + " -k'" + '\n').getBytes());
            } else if (type == 1) {
                core.getLogger().writeLine("HARD cutting network connection to " + target + " from " + gateway,1);
                stdin.write((Core.EXECUTE + " 'python3 /CORE/MegaCut/megacut.py " + target + " " + gateway + " -m'" + '\n').getBytes());
            } else if (type == 2) {
                core.getLogger().writeLine("Cutting network connection (20s) to " + target + " from " + gateway,1);
                stdin.write((Core.EXECUTE + " 'python3 /CORE/MegaCut/megacut.py " + target + " " + gateway + " -b'" + '\n').getBytes());
            } else if (type == 3) {
                core.getLogger().writeLine("Enabling connection to " + target + " from " + gateway,1);
                stdin.write((Core.EXECUTE + " 'python3 /CORE/MegaCut/megacut.py " + target + " " + gateway + " -r'" + '\n').getBytes());
            }

            stdin.write(("\n").getBytes());
            stdin.flush();
            stdin.close();
            ArrayList<String> out = new ArrayList<>();
            ArrayList<String> outerror = new ArrayList<>();
            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                out.add(line);
                core.getLogger().writeLine(line,2);
            }
            br.close();
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                outerror.add(line);
                core.getLogger().writeLine(line,3);
            }
            
            
            br.close();
            process.waitFor();
            process.destroy();
        } catch (IOException e) {
        } catch (InterruptedException ex) {
        }

        return null;
    }

    public void kill() {
        process.destroy();
    }

    @Override
    protected void onPostExecute(Void result) {
        super.onPostExecute(result);
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        super.onProgressUpdate(values);

    }


}
