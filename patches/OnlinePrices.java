package cl.compararm.app;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Public web search results for only Jumbo and Santa Isabel. No affiliation,
 * permission from retailers, GTIN matching, or in-store price verification.
 * Returns exact listing names for the user to inspect; never infers "cheapest
 * identical product" without an exact identifier.
 */
final class OnlinePrices {
    static class Listing {
        final String store, name, link;
        final int price;
        Listing(String store,String name,int price,String link) {
            this.store=store;this.name=name;this.price=price;this.link=link;
        }
    }
    static class Result {
        final List<Listing> listings=new ArrayList<>();
        final List<String> problems=new ArrayList<>();
    }
    private static final String USER_AGENT="ComparaRM/0.2 Android (public catalog lookup)";
    private static String request(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(10000);c.setReadTimeout(10000);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent",USER_AGENT);
        try {
            int status=c.getResponseCode();
            if(status!=200) throw new IllegalStateException("HTTP "+status);
            try(InputStream in=c.getInputStream();
                BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {
                StringBuilder builder=new StringBuilder();String line;
                while((line=reader.readLine())!=null) {
                    builder.append(line);
                    if(builder.length()>2_000_000) throw new IllegalStateException("Respuesta demasiado grande");
                }
                return builder.toString();
            }
        } finally {c.disconnect();}
    }
    static Result query(String query) {
        Result out=new Result();
        String safe=query==null?"":query.trim();
        if(safe.length()<2) {out.problems.add("Escribe al menos dos caracteres.");return out;}
        if(safe.length()>90) safe=safe.substring(0,90);
        find(out,"Jumbo","pwcdauseo-zone.cnstrc.com","key_JopvNXKS61kwGkBe","https://www.jumbo.cl",safe);
        find(out,"Santa Isabel","ac.cnstrc.com","key_c73M3GMIWJ8AcNnd","https://www.santaisabel.cl",safe);
        return out;
    }
    private static int readClp(JSONObject data) {
        for(String k:new String[]{"sellingPrice","price"}) {
            Object v=data.opt(k);
            if(v instanceof Number) {
                int n=(int)Math.round(((Number)v).doubleValue());
                if(n>0 && n<20_000_000) return n;
            }
            if(v instanceof String) {
                String raw=((String)v).trim();
                if(raw.matches("[0-9]+(\\.[0-9]+)?")) {
                    try {
                        int n=(int)Math.round(Double.parseDouble(raw));
                        if(n>0 && n<20_000_000)return n;
                    } catch(Exception ignored){}
                }
            }
        }
        return -1;
    }
    private static void find(Result out,String store,String host,String key,String home,String query) {
        try {
            String params="key="+URLEncoder.encode(key,"UTF-8")
                +"&i="+URLEncoder.encode(UUID.randomUUID().toString(),"UTF-8")
                +"&s=1&num_results_per_page=8";
            String url="https://"+host+"/search/"+URLEncoder.encode(query,"UTF-8")+"?"+params;
            JSONArray rows=new JSONObject(request(url)).optJSONObject("response").optJSONArray("results");
            if(rows==null) {out.problems.add(store+": formato de respuesta no reconocido.");return;}
            for(int i=0;i<rows.length();i++) {
                JSONObject obj=rows.optJSONObject(i);
                if(obj==null)continue;
                JSONObject data=obj.optJSONObject("data");
                if(data==null)continue;
                String name=obj.optString("value",data.optString("ProductName","")).trim();
                int price=readClp(data);
                if(name.isEmpty() || price<=0)continue;
                String path=data.optString("url",data.optString("DetailUrl",""));
                String link=home;
                if(!path.isEmpty()) {
                    try {
                        Uri parsed=Uri.parse(path.startsWith("/")?home+path:path);
                        String hostname=parsed.getHost();
                        if("https".equalsIgnoreCase(parsed.getScheme()) &&
                           hostname!=null &&
                           (hostname.endsWith(".jumbo.cl") || hostname.equals("www.jumbo.cl") ||
                            hostname.endsWith(".santaisabel.cl") || hostname.equals("www.santaisabel.cl") ||
                            hostname.equals("www.sisa.cl")))link=parsed.toString();
                    } catch(Exception ignored){}
                }
                out.listings.add(new Listing(store,name,price,link));
            }
        } catch(Exception ex) {
            out.problems.add(store+": sin datos disponibles ("+ex.getClass().getSimpleName()+").");
        }
    }
    static String nameForBarcode(String ean) {
        if(ean==null||!ean.matches("\\d{8,14}"))return "";
        try {
            String base="https://world.openfoodfacts.org/api/v2/product/"+URLEncoder.encode(ean,"UTF-8")
                +"?fields=product_name,brands,quantity";
            JSONObject document=new JSONObject(request(base));
            if(document.optInt("status")!=1)return "";
            JSONObject product=document.optJSONObject("product");
            if(product==null)return "";
            String brand=product.optString("brands","").split(",")[0].trim();
            String name=product.optString("product_name","").trim();
            String quantity=product.optString("quantity","").trim();
            String combined=(brand+" "+name+" "+quantity).trim().replaceAll("\\s+"," ");
            return combined.length()>90?combined.substring(0,90):combined;
        } catch(Exception ex) {return "";}
    }
}