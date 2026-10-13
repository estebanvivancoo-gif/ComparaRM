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
        findUnimarc(out,safe);
        findTottus(out,safe);
        findLider(out,safe);
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

    private static int clp(Object raw) {
        if(raw instanceof Number) {
            int n=(int)Math.round(((Number)raw).doubleValue());
            return n>0 && n<20_000_000?n:-1;
        }
        if(raw==null)return -1;
        String v=String.valueOf(raw).trim().replaceAll(",00$","");
        v=v.replaceAll("[^0-9]","");
        try {
            int n=Integer.parseInt(v);
            return n>0&&n<20_000_000?n:-1;
        } catch(Exception e){return -1;}
    }
    private static String downloadPage(String url) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(6500);
        conn.setReadTimeout(6500);
        conn.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
        conn.setRequestProperty("Accept","text/html,application/json");
        try {
            int status=conn.getResponseCode();
            if(status!=200)throw new IllegalStateException("HTTP "+status);
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))) {
                StringBuilder builder=new StringBuilder();
                char[] buf=new char[4096];int n;
                while((n=reader.read(buf))!=-1) {
                    builder.append(buf,0,n);
                    if(builder.length()>4_000_000)throw new IllegalStateException("Respuesta demasiado grande");
                }
                return builder.toString();
            }
        } finally{conn.disconnect();}
    }
    private static void findUnimarc(Result out,String query) {
        HttpURLConnection conn=null;
        try {
            conn=(HttpURLConnection)new URL("https://bff-unimarc-ecommerce.unimarc.cl/catalog/product/search").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(7000);conn.setReadTimeout(7000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json");
            conn.setRequestProperty("Accept","application/json");
            conn.setRequestProperty("User-Agent",USER_AGENT);
            conn.setRequestProperty("channel","UNIMARC");
            conn.setRequestProperty("source","web");
            conn.setRequestProperty("version","1.0.0");
            JSONObject body=new JSONObject();
            body.put("from","0");body.put("to","7");
            body.put("searching",query);body.put("promotionsOnly",false);
            body.put("orderBy","");body.put("userTriggered",true);
            byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);
            try(java.io.OutputStream output=conn.getOutputStream()) {output.write(data);}
            if(conn.getResponseCode()!=200)throw new IllegalStateException("HTTP "+conn.getResponseCode());
            StringBuilder builder=new StringBuilder();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))) {
                String line;while((line=reader.readLine())!=null) {
                    builder.append(line);
                    if(builder.length()>1_500_000)throw new IllegalStateException("Respuesta demasiado grande");
                }
            }
            JSONArray products=new JSONObject(builder.toString()).optJSONArray("availableProducts");
            if(products==null)throw new IllegalStateException("Catálogo sin resultados estructurados");
            for(int i=0;i<products.length();i++) {
                JSONObject p=products.optJSONObject(i);
                if(p==null)continue;
                JSONObject item=p.optJSONObject("item"), info=p.optJSONObject("price");
                if(item==null||info==null)continue;
                String name=item.optString("name","").trim();
                int price=clp(info.opt("price"));
                if(name.isEmpty()||price<0)continue;
                String slug=item.optString("slug","");
                String link="https://www.unimarc.cl";
                if(slug.startsWith("/"))link+="/product"+slug;
                out.listings.add(new Listing("Unimarc",name,price,link));
            }
        }catch(Exception e) {
            out.problems.add("Unimarc: fuente no disponible ("+e.getClass().getSimpleName()+").");
        }finally{if(conn!=null)conn.disconnect();}
    }
    private static JSONObject nextData(String html) throws Exception {
        java.util.regex.Matcher m=java.util.regex.Pattern.compile(
            "<script[^>]+id\\s*=\\s*['\\\"]__NEXT_DATA__['\\\"][^>]*>(.*?)</script>",
            java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE
        ).matcher(html);
        if(!m.find())throw new IllegalStateException("Página sin catálogo JSON");
        return new JSONObject(m.group(1));
    }
    private static void findTottus(Result out,String query) {
        try {
            String html=downloadPage("https://www.tottus.cl/tottus-cl/buscar?Ntt="+URLEncoder.encode(query,"UTF-8"));
            JSONObject root=nextData(html).optJSONObject("props");
            JSONObject props=root==null?null:root.optJSONObject("pageProps");
            JSONArray list=props==null?null:props.optJSONArray("results");
            if(list==null)throw new IllegalStateException("Productos no disponibles");
            for(int i=0;i<Math.min(8,list.length());i++){
                JSONObject p=list.optJSONObject(i);
                if(p==null)continue;
                JSONArray prices=p.optJSONArray("prices");
                if(prices==null)continue;
                int price=-1;
                for(int j=0;j<prices.length();j++) {
                    JSONObject pr=prices.optJSONObject(j);
                    if(pr==null)continue;
                    JSONArray values=pr.optJSONArray("price");
                    int amount=values!=null&&values.length()>0?clp(values.opt(0)):-1;
                    if(amount>0&&(price<=0||"internetPrice".equals(pr.optString("type"))))price=amount;
                    if("internetPrice".equals(pr.optString("type")) && amount>0)break;
                }
                String name=p.optString("displayName","").trim();
                if(name.isEmpty()||price<0)continue;
                String path=p.optString("url","");
                String link=path.startsWith("/")?"https://www.tottus.cl"+path:"https://www.tottus.cl";
                out.listings.add(new Listing("Tottus",name,price,link));
            }
        }catch(Exception e){
            out.problems.add("Tottus: consulta automática bloqueada o sin datos ("+e.getClass().getSimpleName()+").");
        }
    }
    private static void findLider(Result out,String query) {
        try {
            String html=downloadPage("https://super.lider.cl/search?query="+URLEncoder.encode(query,"UTF-8"));
            JSONObject root=nextData(html);
            appendLiderProducts(out,root,0);
            if(!containsStore(out,"Líder"))throw new IllegalStateException("Sin catálogo legible");
        } catch(Exception e) {
            out.problems.add("Líder: consulta automática no disponible ("+e.getClass().getSimpleName()+").");
        }
    }
    private static boolean containsStore(Result out,String store) {
        for(Listing item:out.listings)if(store.equals(item.store))return true;
        return false;
    }
    private static void appendLiderProducts(Result out,Object node,int depth) {
        if(node==null||depth>14||countStore(out,"Líder")>=8)return;
        if(node instanceof JSONObject) {
            JSONObject obj=(JSONObject)node;
            if("Product".equals(obj.optString("__typename"))) {
                JSONObject priceInfo=obj.optJSONObject("priceInfo");
                int price=priceInfo==null?-1:clp(priceInfo.opt("linePrice"));
                if(price<0&&priceInfo!=null)price=clp(priceInfo.opt("itemPrice"));
                String name=obj.optString("name","").trim();
                if(price>0 && !name.isEmpty()) {
                    String path=obj.optString("canonicalUrl","");
                    String link=path.startsWith("/")?"https://super.lider.cl"+path:"https://super.lider.cl";
                    out.listings.add(new Listing("Líder",name,price,link));
                }
                return;
            }
            java.util.Iterator<String> keys=obj.keys();
            while(keys.hasNext() && countStore(out,"Líder")<8)appendLiderProducts(out,obj.opt(keys.next()),depth+1);
        }else if(node instanceof JSONArray){
            JSONArray arr=(JSONArray)node;
            for(int i=0;i<Math.min(30,arr.length())&&countStore(out,"Líder")<8;i++)appendLiderProducts(out,arr.opt(i),depth+1);
        }
    }
    private static int countStore(Result out,String store) {
        int i=0;for(Listing row:out.listings)if(store.equals(row.store))i++;return i;
    }

}