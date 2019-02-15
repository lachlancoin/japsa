package japsa.bio.phylo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
/** extract taxon ids matching speciesIndex from a list of assembly summary files */
public class GetTaxonID {
  Set<String> taxon_set = new HashSet<String>();
	Map<String, String> name2Taxa = new HashMap<String, String>();
	Map<String, String> name2Taxa4 = new HashMap<String, String>();
	Map<String, String> name2Taxa3 = new HashMap<String, String>();
	Map<String, String> name2Taxa2 = new HashMap<String, String>();



	Map<String, String> taxa2Sci = new HashMap<String, String>();
  public GetTaxonID(){
  }
  
  public String getSciName(final String specName){
	  String taxa = getTaxa(specName);
	  if(taxa!=null){
			 return taxa2Sci.get(taxa);
		 }
	  else return null;
  }
  
  public String getTaxa(final String specName){
	  String slug =  Slug.toSlug(specName, "");
		String slug4 =  Slug.toSlug(specName, 4,"");
		String slug3 =  Slug.toSlug(specName, 3,"");
		String slug2 =  Slug.toSlug(specName, 2,"");
	 String taxa = this.name2Taxa.get(slug);
	 if(taxa==null) taxa = name2Taxa4.get(slug4);
	 if(taxa==null) taxa = name2Taxa3.get(slug3);
	 if(taxa==null) taxa = name2Taxa2.get(slug2);
	 //if(specName.indexOf("229E-related")>=0){
	//	  System.err.println('h');
	 // }
	// err.println(specName+"->"+slug1+"->"+slug2+"->"+slug3+"->"+taxa);
	 return taxa;
  }
  void putTaxa(String nme, String taxa){
	 
	  String slug = Slug.toSlug(nme, "");
	  String slug4 = Slug.toSlug(nme, 4,"");
	  String slug3 = Slug.toSlug(nme, 3,"");
	  String slug2 = Slug.toSlug(nme, 2,"");
	  name2Taxa.put(slug, taxa);
	  name2Taxa4.put(slug4, taxa);
	  name2Taxa3.put(slug3, taxa);
	  name2Taxa2.put(slug2, taxa);
	  if(nme.indexOf("229E-related")>=0){
		  System.err.println(slug);
		  System.err.println('h');
	  }
  }
  PrintWriter err;
  
  public Map<String, String> nodeToParent = new HashMap<String,String >();
  
  public void addNodeDmp(File file) throws IOException{
	  BufferedReader br = getBR(file);
	  String st = "";
	  while((st = br.readLine())!=null){
		  String[] str = st.split("\\|");
		
			 nodeToParent.put(str[0].trim(), str[1].trim());
		 
	  }
	  br.close();
  }
  
  
  
  public GetTaxonID(File file, File names_dmp)  throws IOException{
	//  err = new PrintWriter(new FileWriter(new File("err.txt")));
	  if(file!=null && file.exists()){
		  BufferedReader br = getBR(file);
		  String st = "";
		  while((st = br.readLine())!=null){
			  taxon_set.add(st.split("\\s+")[0]);
		  }
		  br.close();
	  }
	  if(names_dmp.exists()){
		  BufferedReader br = getBR(names_dmp);
		  String st = "";
		  while((st = br.readLine())!=null){
			  String[] str = st.split("\t");
			  String taxa = str[0];
			  //if(taxon_set.contains(taxa)){
				  	String nme = str[2];;
				  	String type = str[6];
				 putTaxa(nme, taxa);
				
				  if(type.startsWith("scientific")){
					  taxa2Sci.put(taxa, nme);
				  }
			  //}
			
		  }
		  br.close();
	  }
		// TODO Auto-generated constructor stub
	}
  
  public void print(File out) throws IOException{
	  PrintWriter pw = new PrintWriter(new FileWriter(out));
	  for(Iterator<String> it = taxon_set.iterator(); it.hasNext();){
		  pw.println(it.next());
	  }
	  pw.close();
  }
public static void main(String[] args){
	  try{
		 GetTaxonID gid = new GetTaxonID(new File("taxonid"), new File("taxdump/names.dmp"));
		  gid.process(new File("speciesIndex"));
		  gid.print(new File("taxonid.new"));
		 
	  }catch(Exception exc){
		  exc.printStackTrace();
	  }
  }

static BufferedReader getBR(File file)throws IOException{
	 BufferedReader br;
		if(file.getName().endsWith(".gz")){
			br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file))));
		}
		else{
			br = new BufferedReader(new FileReader(file));
		}
		return br;
}
 public void process(File file)throws IOException {
	  BufferedReader br = getBR(file);
		String st;
		while((st = br.readLine())!=null){
		 String[] str = st.split("\\s+");
		 String taxa = this.processAlias(str, st);
		if(taxa!=null) {
			
			this.taxon_set.add(taxa);
		}
		}
}
 
 /* a few special cases */
 static String[] African_Cassava_Mosaic =  
		 (">AF|>AJ|>AM|>AY|>DQ|>EF|>EU|>FJ|>FM|>FN|>FR|>GQ|>GU|>HE|>HG|>HM|>HQ|>J0|>JF|>JN|>JX|>KC|>KF|>KM|>KP|>KR"
		 + "|>KT|>KU|>KX|>X1|>X6|>Z2|>Z8|>AF|>AJ|>AM|>AY|>DQ|>EF|>EU|>FJ|>FM|>FN|>FR|>GQ|"
		 + ">GU|>HE|>HG|>HM|>HQ|>J0|>JF|>JN|>JX|>KC|>KF|>KM|>KP|>KR|>KT|>KU|>KX|>X1|>X6|>Z2|>Z8|>JQ|>KJ").split("\\|");
 static String[][] spec = new String[][]{
		 ">chr	      :>HLA        :>Kqp".split(":"),
		 "Homo_sapiens:Homo_sapiens:Klebsiella_quasipneumoniae".split(":")
 	};
 	static{
 		for(int i=0; i<spec[0].length; i++){
 			spec[0][i] = spec[0][i].trim();
 		}
 		
 	}
 	String processAlias(String[] str, String st){
 	 String alias1 = collapse(str, 1);
 	/* int compg = alias1.indexOf(", complete genome");
 	 if(compg>=0){
 		 alias1 = alias1.substring(0, compg);
 	 }*/
	 if(  st.indexOf("GRCh38")>=0){
		 alias1= "Homo_sapiens";
		// str[0] = specName;
	 }else{
		 for(int i=0; i<African_Cassava_Mosaic.length; i++){
			 if(st.startsWith(African_Cassava_Mosaic[i])){
				 alias1="African_Cassava_Mosaic";
			 }
		 }
		 for(int i=0; i<spec[0].length; i++){
			 if(st.startsWith(spec[0][i])){
				 alias1=spec[1][i];
			 }
		 }
		
	 }
	 if(alias1.startsWith(">")) alias1 = alias1.substring(1);
	 
	return  this.getTaxa(alias1);
	
 	}
 	
 	 public static String collapse(String[] line, int start) {
 		int end = line.length; String string = " ";
 		StringBuffer sb = new StringBuffer(line[start]);

 		for(int i=start+1; i<end; i++){
 			sb.append(string+line[i]);
 		}
 		return sb.toString();
 	}
 
 
/*Map<String, String> slugToTaxon = new HashMap<String, String>();
//Map<String, String> slugToTaxonShort = new HashMap<String, String>();
  void processGenBank(String[] files) throws IOException{
	  for(int i=0; i<files.length; i++){
		  File file = new File(files[i]);
	  BufferedReader br;
		if(file.getName().endsWith(".gz")){
			br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file))));
		}
		else{
			br = new BufferedReader(new FileReader(file));
		}
		String st = "";
		while((st = br.readLine())!=null){
			if(st.startsWith("#")) continue;
			String[] str = st.split("\t");
			String tax = str[6];
			String species = str[7];
			String slug = Slug.toSlug(species, "");
			String slugs = Slug.toSlug(species,2, "");
					
			slugToTaxon.put(slug, tax);
			slugToTaxonShort.put(slugs, tax);
			
			//
		}
	  }
  }*/


  
}
