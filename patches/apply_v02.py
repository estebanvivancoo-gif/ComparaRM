from pathlib import Path

root=Path("ComparaRM_v0.1")
main=root/"app/src/main/java/cl/compararm/app/MainActivity.java"
layout=root/"app/src/main/res/layout/activity_main.xml"
manifest=root/"app/src/main/AndroidManifest.xml"
build=root/"app/build.gradle"

def change(text, old, new):
    if old not in text: raise RuntimeError("Falta ancla: "+old[:90])
    return text.replace(old,new,1)

text=main.read_text()
text=change(text,"    private Uri photoUri;\n","    private Uri photoUri;\n    private TextView liveStatus;\n    private LinearLayout liveResults;\n    private int requestVersion=0;\n")
text=change(text,'        findViewById(R.id.searchButton).setOnClickListener(v -> searchAndRender());','        findViewById(R.id.searchButton).setOnClickListener(v -> {searchAndRender();queryOnline();});')
text=change(text,'        searchInput.setOnEditorActionListener((v,actionId,event)-> {searchAndRender();return true;});',
'''        searchInput.setOnEditorActionListener((v,actionId,event)-> {searchAndRender();queryOnline();return true;});
        liveStatus=findViewById(R.id.liveStatus);
        liveResults=findViewById(R.id.liveResults);
        findViewById(R.id.onlineButton).setOnClickListener(v -> queryOnline());''')
text=change(text,'            showSelected(exact);\n            return;','            showSelected(exact);\n            searchInput.setText(exact.name+" "+exact.units);\n            queryOnline();\n            return;')
text=change(text,'            showSelected(proposed);\n            return;','            showSelected(proposed);\n            searchInput.setText(proposed.name+" "+proposed.units);\n            queryOnline();\n            return;')
text=change(text,'            searchInput.setText(lastScannedCode);\n','            searchInput.setText(lastScannedCode);\n            lookupBarcode(lastScannedCode);\n')
text=change(text,'            searchInput.setText("");\n        } else {\n            setStatus("No se reconocieron códigos ni texto legible.',
'''            String firstLine=recognizedText.trim().split("\\r?\\n")[0].trim();
            searchInput.setText(firstLine.length()>75?firstLine.substring(0,75):firstLine);
            if(firstLine.length()>2)queryOnline();
        } else {
            setStatus("No se reconocieron códigos ni texto legible.''')

methods=r'''
    private void queryOnline() {
        String q=searchInput.getText().toString().trim();
        final int version=++requestVersion;
        liveResults.removeAllViews();
        if(q.length()<2) {
            liveStatus.setText("Escribe o escanea un producto para consultar los buscadores web.");
            return;
        }
        liveStatus.setText("Consultando Jumbo y Santa Isabel…");
        new Thread(() -> {
            OnlinePrices.Result result=OnlinePrices.query(q);
            runOnUiThread(() -> {
                if(version!=requestVersion || isFinishing() || isDestroyed())return;
                showOnline(result,q);
            });
        }).start();
    }
    private void lookupBarcode(String code) {
        int version=++requestVersion;
        liveStatus.setText("Código "+code+" leído. Buscando su nombre…");
        new Thread(() -> {
            String title=OnlinePrices.nameForBarcode(code);
            runOnUiThread(() -> {
                if(version!=requestVersion || isFinishing() || isDestroyed())return;
                if(!title.isEmpty()) {
                    searchInput.setText(title);
                    setStatus("Código "+code+" encontrado en catálogo colaborativo: "+title+". Verifica marca y tamaño.");
                    queryOnline();
                } else {
                    liveStatus.setText("No pude identificar ese código. Busca por marca y presentación o fotografía la parte frontal.");
                    liveResults.removeAllViews();
                }
            });
        }).start();
    }
    private void showOnline(OnlinePrices.Result result,String query) {
        liveResults.removeAllViews();
        if(result.listings.isEmpty()) {
            liveStatus.setText("Sin precios web encontrados para «"+query+"». Esto no significa que no exista en las tiendas.");
        } else {
            java.text.SimpleDateFormat df=new java.text.SimpleDateFormat("dd/MM HH:mm",new java.util.Locale("es","CL"));
            liveStatus.setText(result.listings.size()+" productos publicados online · "+df.format(new java.util.Date())+
                ". No se verificó el mismo código EAN ni el precio de tu sucursal.");
        }
        for(OnlinePrices.Listing item:result.listings) {
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(px(12),px(11),px(12),px(11));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.topMargin=px(6);card.setLayoutParams(lp);
            card.setBackground(bg(0xFFF6FAFC,0xFFDDE9ED));
            card.addView(label(item.store+" · "+Catalog.clp(item.price),15,true,0xFF087A76));
            TextView name=label(item.name,13,false,0xFF173441);
            name.setPadding(0,px(4),0,0);card.addView(name);
            TextView hint=label("Ver ficha en supermercado ↗",11,false,0xFF426E8E);
            hint.setPadding(0,px(4),0,0);card.addView(hint);
            card.setOnClickListener(v -> {
                try {startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,Uri.parse(item.link)));}
                catch(Exception ignored){toast("No se pudo abrir la tienda.");}
            });
            liveResults.addView(card);
        }
        if(!result.problems.isEmpty()) {
            TextView issues=label("Avisos: "+android.text.TextUtils.join(" · ",result.problems),11,false,0xFF975320);
            issues.setPadding(0,px(8),0,px(4));liveResults.addView(issues);
        }
        TextView fallback=label("Otras cadenas: búsqueda externa (sin precio integrado)",12,true,0xFF425B66);
        fallback.setPadding(0,px(16),0,px(4));liveResults.addView(fallback);
        String[][] stores={
            {"Líder","https://super.lider.cl/search?q="},
            {"Unimarc","https://www.unimarc.cl/search?q="},
            {"Tottus","https://www.tottus.cl/tottus-cl/buscar?Ntt="}
        };
        for(String[] store:stores) {
            TextView link=label("Buscar en "+store[0]+" ↗",13,true,0xFF14736E);
            link.setPadding(px(12),px(10),0,px(10));
            link.setOnClickListener(v -> {
                try {
                    String target=store[1]+java.net.URLEncoder.encode(query,"UTF-8");
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,Uri.parse(target)));
                } catch(Exception e){toast("No se pudo abrir el supermercado.");}
            });
            liveResults.addView(link);
        }
    }
'''
text=change(text,'    private void setStatus(String message) { scanStatus.setText(message); }',methods+'\n    private void setStatus(String message) { scanStatus.setText(message); }')
main.write_text(text)

xml=layout.read_text()
xml=change(xml,'<Button android:id="@+id/searchButton" android:layout_width="match_parent" android:layout_height="44dp" android:layout_marginTop="8dp" android:allCaps="false" android:text="Buscar productos"/>',
'''<Button android:id="@+id/searchButton" android:layout_width="match_parent" android:layout_height="44dp" android:layout_marginTop="8dp" android:textAllCaps="false" android:text="Buscar y consultar precios web"/>
            <Button android:id="@+id/onlineButton" android:layout_width="match_parent" android:layout_height="50dp" android:layout_marginTop="8dp" android:background="@drawable/button_primary" android:textAllCaps="false" android:text="CONSULTAR PRECIOS ONLINE" android:textColor="#FFFFFF" android:textStyle="bold"/>''')
old='''        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_margin="16dp" android:background="@drawable/surface_card" android:padding="16dp" android:orientation="vertical">
            <TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:text="02   Productos del catálogo"'''
new='''        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginLeft="16dp" android:layout_marginRight="16dp" android:layout_marginTop="16dp" android:background="@drawable/surface_card" android:padding="16dp" android:orientation="vertical">
            <TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:text="02   Precios publicados en Internet" android:textColor="#112D3D" android:textSize="18sp" android:textStyle="bold"/>
            <TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="6dp" android:text="Jumbo y Santa Isabel: consulta experimental. Los resultados pueden ser productos diferentes. Verifica marca, formato, ofertas y precio de tu sucursal." android:textColor="#4B6874" android:textSize="13sp"/>
            <TextView android:id="@+id/liveStatus" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="10dp" android:text="Ingresa un producto y pulsa Consultar precios online." android:textColor="#326B78" android:textSize="12sp"/>
            <LinearLayout android:id="@+id/liveResults" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="8dp" android:orientation="vertical"/>
        </LinearLayout>
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_margin="16dp" android:background="@drawable/surface_card" android:padding="16dp" android:orientation="vertical">
            <TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:text="03   Catálogo local"'''
xml=change(xml,old,new)
xml=xml.replace('PILOTO · SIN PRECIOS ONLINE VERIFICADOS','PILOTO · BÚSQUEDA WEB DE 2 CADENAS')
xml=xml.replace('Valores de ejemplo ficticios. Tus registros manuales se muestran por separado.','Los resultados web, las cifras ficticias y tus registros manuales se muestran separados.')
xml=xml.replace('03   Comparación de precios','04   Datos de demostración y registros')
xml=xml.replace('ComparaRM 0.1 · Prototipo local · Santiago y RM\\nSin conexión a tiendas ni validación comercial','ComparaRM 0.2 · Consulta experimental · Santiago y RM\\nNo comprueba el precio en caja ni la coincidencia exacta del EAN')
xml=xml.replace('android:allCaps=','android:textAllCaps=')
layout.write_text(xml)

m=manifest.read_text()
m=change(m,'    <application android:', '    <uses-permission android:name="android.permission.INTERNET" />\n    <application android:')
m=m.replace('<!-- No Internet permissions: all catalog and user observations remain on-device in this prototype. -->','<!-- Public catalog lookup; personal observations remain local. -->')
manifest.write_text(m)

b=build.read_text().replace('versionCode 1','versionCode 2').replace("versionName '0.1.0'","versionName '0.2.0'")
build.write_text(b)
(root/"app/src/main/java/cl/compararm/app/OnlinePrices.java").write_bytes(Path("patches/OnlinePrices.java").read_bytes())
print("ComparaRM v0.2 files patched")
