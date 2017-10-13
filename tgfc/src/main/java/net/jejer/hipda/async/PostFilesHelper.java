package net.jejer.hipda.async;

import net.jejer.hipda.okhttp.OkHttpHelper;
import net.jejer.hipda.utils.FormFile;
import net.jejer.hipda.utils.HiUtils;
import net.jejer.hipda.utils.Logger;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.HttpURLConnection;

import java.net.URL;
import java.util.Map;

/**
 * Created by raymond on 2017/10/11.
 */


public class PostFilesHelper {

    private static final int UPLOAD_CONNECT_TIMEOUT = 15 * 1000;
    private static final int UPLOAD_READ_TIMEOUT = 5 * 60 * 1000;


    /**
     *多文件上传
     * @param path 上传路径
     * @param params 请求参数 key为参数名,value为参数值
     * @param files 上传文件
     */
    public static String post(String path, Map<String, String> params, FormFile[] files) throws Exception{

        String authCookie = OkHttpHelper.getInstance().getAuthCookie();

        final String BOUNDARY = "---------------------------7d" + getBoundry(); //数据分隔线
        final String endline = "--" + BOUNDARY + "--\r\n";//数据结束标志

        int fileDataLength = 0;
        for(FormFile uploadFile : files){//得到文件类型数据的总长度
            StringBuilder fileExplain = new StringBuilder();
            fileExplain.append("--");
            fileExplain.append(BOUNDARY);
            fileExplain.append("\r\n");
            fileExplain.append("Content-Disposition: form-data;name=\"attach[]\";filename=\""+ uploadFile.getFilname() + "\"\r\n");
            fileExplain.append("Content-Type: "+ uploadFile.getContentType()+"\r\n\r\n");
            fileExplain.append("\r\n");
            fileExplain.append("--");
            fileExplain.append(BOUNDARY);
            fileExplain.append("\r\n");
            fileExplain.append("Content-Disposition: form-data;name=\"localid[]\"\r\n\r\n");
            fileExplain.append(uploadFile.getParameterName());
            fileExplain.append("\r\n");
            fileExplain.append("--");
            fileExplain.append(BOUNDARY);
            fileExplain.append("\r\n");
            fileExplain.append("Content-Disposition: form-data;name=\"attachprice[]\"\r\n\r\n");
            fileExplain.append("0");
            fileExplain.append("\r\n");
            fileExplain.append("--");
            fileExplain.append(BOUNDARY);
            fileExplain.append("\r\n");
            fileExplain.append("Content-Disposition: form-data;name=\"attachdesc[]\"\r\n\r\n");
            fileExplain.append("\r\n");
            fileDataLength += fileExplain.length();
            if(uploadFile.getInStream()!=null){
                fileDataLength += uploadFile.getFile().length();
            }else{
                fileDataLength += uploadFile.getData().length;
            }
        }
        StringBuilder textEntity = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {//构造文本类型参数的实体数据
            textEntity.append("--");
            textEntity.append(BOUNDARY);
            textEntity.append("\r\n");
            textEntity.append("Content-Disposition: form-data; name=\""+ entry.getKey() + "\"\r\n\r\n");
            textEntity.append(entry.getValue());
            textEntity.append("\r\n");
        }
        //计算传输给服务器的实体数据总长度
        int dataLength = textEntity.toString().getBytes("GBK").length + fileDataLength +  endline.getBytes().length;

        String lenstr = Integer.toString(dataLength);
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        String mMessage = "";
        String res = "";
        try {
            URL url = new URL(path);

            urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.setRequestProperty("User-Agent", HiUtils.getUserAgent());
            urlConnection.setRequestProperty("Cookie", "tgc_auth=" + authCookie);

            urlConnection.setConnectTimeout(UPLOAD_CONNECT_TIMEOUT);
            urlConnection.setReadTimeout(UPLOAD_READ_TIMEOUT);
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);
            urlConnection.setRequestMethod("POST");
            urlConnection.setUseCaches(false);
            urlConnection.setRequestProperty("Connection", "Keep-Alive");
            urlConnection.setRequestProperty("Content-type", "multipart/form-data;boundary=" + BOUNDARY);
            urlConnection.setRequestProperty("Content-Length", lenstr);
            urlConnection.setFixedLengthStreamingMode(dataLength);
            urlConnection.connect();

            out = new BufferedOutputStream(urlConnection.getOutputStream());
            //发送文本类型的实体数据
            out.write(textEntity.toString().getBytes("GBK"));

            //把所有文件类型的实体数据发送出来
            for(FormFile uploadFile : files){
                StringBuilder fileEntity = new StringBuilder();
                fileEntity.append("--");
                fileEntity.append(BOUNDARY);
                fileEntity.append("\r\n");
                fileEntity.append("Content-Disposition: form-data;name=\"attach[]\";filename=\""+ uploadFile.getFilname() + "\"\r\n");
                fileEntity.append("Content-Type: "+ uploadFile.getContentType()+"\r\n\r\n");
                //发送文件的文本数据
                out.write(fileEntity.toString().getBytes());
                if(uploadFile.getInStream()!=null){
                    byte[] buffer = new byte[1024];
                    int len = 0;
                    while((len = uploadFile.getInStream().read(buffer, 0, 1024))!=-1){
                        //发送文件
                        out.write(buffer, 0, len);
                    }
                    uploadFile.getInStream().close();
                }else{
                    //发送文件
                    out.write(uploadFile.getData(), 0, uploadFile.getData().length);
                }
                out.write("\r\n".getBytes());
                //发送文件参数
                StringBuilder fileParams = new StringBuilder();
                fileParams.append("--");
                fileParams.append(BOUNDARY);
                fileParams.append("\r\n");
                fileParams.append("Content-Disposition: form-data;name=\"localid[]\"\r\n\r\n");
                fileParams.append(uploadFile.getParameterName());
                fileParams.append("\r\n");
                fileParams.append("--");
                fileParams.append(BOUNDARY);
                fileParams.append("\r\n");
                fileParams.append("Content-Disposition: form-data;name=\"attachprice[]\"\r\n\r\n");
                fileParams.append("0");
                fileParams.append("\r\n");
                fileParams.append("--");
                fileParams.append(BOUNDARY);
                fileParams.append("\r\n");
                fileParams.append("Content-Disposition: form-data;name=\"attachdesc[]\"\r\n\r\n");
                fileParams.append("\r\n");
                out.write(fileParams.toString().getBytes());
            }

            out.write(endline.getBytes());

            out.flush();
            int status = urlConnection.getResponseCode();

            if (status != HttpURLConnection.HTTP_OK) {
                mMessage = "上传错误代码 : " + status;
                return null;
            }
            Logger.v("uploading image, response : " + urlConnection.getResponseCode() + ", " + urlConnection.getResponseMessage());
            InputStream in = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in,"GBK"));
            String inputLine = "";
            while ((inputLine = br.readLine()) != null) {
                res += inputLine;
            }

            Logger.v(res);

        } catch (Exception e) {
            Logger.e("Error uploading image", e);
            mMessage = "上传发生网络错误 : " + e.getMessage();
            return null;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {

                }
            }
            if (urlConnection != null)
                urlConnection.disconnect();
        }

        return res.toString();
    }

    /**
     *单文件上传
     * 提交数据到服务器
     * @param path 上传路径
     * @param params 请求参数 key为参数名,value为参数值
     * @param file 上传文件
     */
    public static String post(String path, Map<String, String> params, FormFile file) throws Exception{
        return post(path, params, new FormFile[]{file});
    }

    private static String getBoundry() {
        StringBuilder sb = new StringBuilder();
        for (int t = 1; t < 12; t++) {
            long time = System.currentTimeMillis() + t;
            if (time % 3 == 0) {
                sb.append((char) time % 9);
            } else if (time % 3 == 1) {
                sb.append((char) (65 + time % 26));
            } else {
                sb.append((char) (97 + time % 26));
            }
        }
        return sb.toString();
    }

}
