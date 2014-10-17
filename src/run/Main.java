package run;

import static nova.lib.my.*;
import nova.lib.jhttp.browser;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import jodd.jerry.Jerry;
import jodd.jerry.JerryFunction;
import jodd.io.findfile.FindFile;
import jodd.props.Props;
import jodd.props.PropsEntry;


public class Main{
	
	private static HashMap<String, LinkedHashMap<String, String>> BOX = new HashMap<String, LinkedHashMap<String, String>>();
	private static Props CONFIG;
	private static boolean FOUND;
	private static browser HTTP = new browser();
	private static String MAINLANG;
	
	public static void main(String args[]) {
		
		try{
			
			DEBUG = true;
	
			String dirsource = null;
			
			// read config
			if(!isFile("geni18n.config")){
				p("geni18n.config not Found!");
				e();
			}else {
				CONFIG = new Props();
				CONFIG.load(new File("geni18n.config"));
			}
			
			// check dir source
			if(CONFIG.getValue("dir_source") == null){
				p("param dir_source not Found!");
				e();
			}else if( !isFolder(CONFIG.getValue("dir_source")) ){
				p("dir "+CONFIG.getValue("dir_source")+" not Found!");
				e();
			}else{
				dirsource = CONFIG.getValue("dir_source").replace("\\", "/");
			}
			
			
			// set use language
			MAINLANG = (CONFIG.getValue("main_lang") == null? "en" : CONFIG.getValue("main_lang"));
			BOX.put(MAINLANG, new LinkedHashMap<String, String>());
			
			
			// run
			search_file(dirsource);
			translate();
		    save_file();
		    
		}catch( Exception e){
			e.printStackTrace();
		}
	    
	    
	}

	private static void search_file(String source) throws Exception{

		@SuppressWarnings("rawtypes")
		FindFile ff = new FindFile()
        .setRecursive(true)
        .setIncludeDirs(true)
        .searchPath(source);
		
		File f;
		String dir = null;
		int num = 1;
		
        p(". >> "+source);
	    while ((f = ff.nextFile()) != null) 
	    {
	        if (f.isDirectory() == true) 
	        {
	            p(". >> " + f.getName());
	            dir = f.getName();
	        } 
	        else 
	        {
	            p(". " + f.getName());
	            if(search_i18n(f.getAbsolutePath(), true)){

	            	BOX.get(MAINLANG).put("_file"+num+"_", (dir!=null ? dir+"/" : "")+f.getName());
	            	search_i18n(f.getAbsolutePath());
	            	num++;
	            	
	            }
	        }
	    }
	    
	}
	
	
	
	
	private static boolean search_i18n(String file) throws Exception{
		return search_i18n(file, false);
	}
	
	private static boolean search_i18n(String file, final boolean check) throws Exception{
		
		FOUND = false;
		
		Jerry j = Jerry.jerry(ReadFile(file));
		j.$("[data-i18n]").each(new JerryFunction() {
            public boolean onNode(Jerry $this, int index) {
            	FOUND = true;
            	if(!check){
            		
                    String word = $this.attr("data-i18n").toLowerCase();
	                BOX.get(MAINLANG).put(word, "");
	                
            	}else{
            		return false;
            	}
                return true;
            }
        });
			
		return FOUND;
		
	}
	

	
	@SuppressWarnings({ "serial", "unchecked", "rawtypes" })
	private static void translate() throws Exception{
		
		String maintext = "";
		if( CONFIG.getValue("translate").equals("true") )
		{
        	Iterator<Entry<String, String>> it = BOX.get(MAINLANG).entrySet().iterator();
            while (it.hasNext()) {
            	String w = it.next().getKey();
            	if(w.indexOf("_file") == -1){
                	maintext += w+(it.hasNext()? "\n" : "");
            	}
            }
		}

		final String text = maintext;
		for (Iterator<PropsEntry> langs = CONFIG.entries().section("lang").iterator(); langs.hasNext(); ) 
		{
			final String lang = langs.next().getValue();
			if(lang.equals(MAINLANG)){
				continue;
			}
			
			BOX.put(lang, new LinkedHashMap<String, String>(BOX.get(MAINLANG)));
			if( CONFIG.getValue("translate").equals("true") )
			{
				String tranjson = HTTP.post("http://translate.google.com/translate_a/t",
						new HashMap<String, String>(){{
							put("client", "t");
							put("sl", MAINLANG);
							put("tl", lang);
							put("hl", lang);
							put("ie","UTF-8");
							put("oe","UTF-8");
							put("q", text);
							
						}}
				).body;
				
				p(tranjson);
				List objson = new Gson().fromJson(tranjson,  new TypeToken<List>() {}.getType());
				List words  = (List) (objson.get(0));
				for (ListIterator<List> itr = words.listIterator(); itr.hasNext(); ) {
					List word = itr.next();
					BOX.get(lang).put(word.get(1).toString().trim(), word.get(0).toString().trim());
				}
				
			}
			
		}
		
		
		
		
	}
	

	private static void save_file() throws Exception{
		
		String dirsave;
		if(CONFIG.getValue("dir_save") == null || !isFolder(CONFIG.getValue("dir_source"))){
			dirsave = "";
		}else{
			dirsave = CONFIG.getValue("dir_save").replace("\\", "/");
		}
		if( !dirsave.substring(dirsave.length()-1).equals("/") ){
			dirsave += "/";
		}
		

		String filename = (CONFIG.getValue("filejs") == null? "resources-locale_[LANG].js" :CONFIG.getValue("filejs"));
		if(filename.indexOf("[LANG]") == -1){
			if(filename.indexOf(".js") == -1){
				filename = filename+"[LANG].js";
			}else{
				filename = filename.replace(".js", "[LANG].js");
			}
		}

        Iterator<Entry<String, LinkedHashMap<String, String>>> it = BOX.entrySet().iterator();
        while (it.hasNext()) {
        	
        	String lang = it.next().getKey();
        	String path = dirsave+filename;
        	String text = "{\r\n";
        	HashMap<String, String> words = BOX.get(lang);

        	Iterator<Entry<String, String>> it2 = words.entrySet().iterator();
            while (it2.hasNext()) {
            	Entry<String, String> w = it2.next();
            	text += (w.getKey().indexOf("_file") > -1? "\r\n" : "" );
            	text += "\t\""+w.getKey()+"\" : \""+w.getValue()+"\""+(it2.hasNext()? ",\r\n" : "\r\n");
            }
    	    
            text += "\r\n}";
    	    SaveFile(text, path.replace("[LANG]", lang));
    	    
        }
        

	}
	

}
