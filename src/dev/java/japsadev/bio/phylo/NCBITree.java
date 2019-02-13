package japsadev.bio.phylo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import pal.misc.Identifier;
import pal.tree.Node;
import pal.tree.NodeUtils;
import pal.tree.SimpleNode;
import pal.tree.SimpleTree;
import pal.tree.Tree;

/** written by Lachlan Coin to parse txt files from commontree */
public  class NCBITree implements CommonTree {
 //Identifier attributes are css for hex value 
 //private static String default_source="src/test/resources/commontree.txt.css.gz";
 
	static String slug_sep = "";//"_";
	 GetTaxonID gid = null;
	 public static void main(String[] args){
		   try{
			   NCBITree t = new NCBITree(new File(args[0]));//, new File(args[1]));
			   
			   t.gid = new GetTaxonID(new File("taxonid"), new File("taxdump/names.dmp"));
				 
				t.addSpeciesIndex(new File(args[1]));

			   System.err.println("here");
			  String[][] taxa =  t.getTaxonomy(t.tree[0].getExternalNode(0).getIdentifier().getName());
			  System.err.println(Arrays.asList(taxa[0]));
			  System.err.println(Arrays.asList(taxa[1]));
			t.print(new File(args[0]+".mod"));
			//  NCBITree t1 = new NCBITree("commontree.txt.out1");
			 // t1.print(new File("commontree.txt.out2"));*/
			//   System.err.println(t.tree.getExternalNodeCount());
		   }catch(Exception exc){
			   exc.printStackTrace();
		   }
	   }
	 
	 public static CommonTree read(File f) throws IOException{
		 return new NCBITree(f);
	 }
	 
	 /* (non-Javadoc)
	 * @see japsadev.bio.phylo.CommonTree#getTaxonomy(java.lang.String)
	 */
	 @Override
	public String[][] getTaxonomy(String in ){
	
		Node node = this.getNode(in);
		
		 List<String> tax = new ArrayList<String>();
		 List<String> css = new ArrayList<String>();
		 while(node!=null){
			 tax.add(node.getIdentifier().getName());
			 css.add((String) node.getIdentifier().getAttribute("css"));
			 if(node.isRoot()) node = null;
			 else node = node.getParent();
		 }
		 return new String[][] {tax.toArray(new String[0]), css.toArray(new String[0])};
	 }
	 
	/* private Node getNode(String in) {
		return this.getNode(this.getSlug(in));
	}*/

	

	public static Tree[] readTree(File f) throws IOException{
		 NCBITree t = new NCBITree(f);
		 return t.tree;
	}
	 
	 public static NCBITree readTree(File f, File speciesIndex) throws IOException{
		 NCBITree t = new NCBITree(f);
		if(speciesIndex!=null){
			t.addSpeciesIndex(speciesIndex);
		}
		return t;
	}
	 
	/* private void putSlug(String str, TreePos tp){
		String slug = Slug.toSlug(str);
		if(slugToPos.containsKey(slug)){
			System.err.println("already had "+str+ "->"+slugToPos.get(slug)+"\n"+tp);
		}
		this.slugToPos.put(slug, tp);
	 }
	 private TreePos getSlug(String str){
		return  this.slugToPos.get(Slug.toSlug(str));
	 }*/
	 
	 private Node getNode(String specName) {
		 if(gid!=null){
			 String specName1 = this.gid.getName(specName);
			 if(specName1!=null) specName = specName1;
		 }
		return  slugToNode.get(Slug.toSlug(specName, this.slug_sep));
	}
	 
	 public Node getSlug(String specName, String alias1){
		 
		 Node n1 = getNode(specName);
		 //
		 //if(n2==null && n1!=null) return n1;
		 //else if(n1==null && n2!=null) return n2;
		 if(n1==null ){
			 Node n2 = getNode(alias1);
			 if(n2==null) return slugToNode.get("unclassified");
			return n2;
		 }
		else return n1;
		
	 }
	 
	 
	 
	 /*str is a line from species index */
	 private void updateTree(String st, int lineno, double bl, PrintWriter missing){
		 String[] str = st.split("\\s+");
		 String specName = str[0];
		 if(str[0].indexOf("GRCh38")>=0){
			 specName= "Homo_sapiens";
			 str[0] = specName;
		 }
		 String alias1 = collapse(str, 2, str.length, " ");
		 Node n = getSlug(specName, alias1);
		 if(n==null) n = this.unclassified;
		 Node newnode =  this.createFromSpeciesFile(str,alias1,  n, lineno, bl);
		 this.putSlug(newnode);
			 
		
		 
	 }
	 
	

	void thinTree( Node n, int i){
		if(n.isLeaf() ) return;
		for(int i1=n.getChildCount()-1; i1>=0;  i1--){
			Node child = n.getChild(i1);
			if(child.isLeaf() && child.getIdentifier().getAttribute("speciesIndex")==null){
				System.err.println("removing "+child.getIdentifier().getName());
				n.removeChild(i1);
			}
			else thinTree(child,i1);
		}
		if(!n.isRoot() && n.getChildCount()==1 && n.getIdentifier().getAttribute("speciesIndex")==null){
			Node child  = n.getChild(0);
			System.err.println("removing1 "+n.getIdentifier().getName());
			n.getParent().setChild(i, child);
		}
		
	}
	
	 
 
/*
private Node getNode(TreePos tp) {
		// TODO Auto-generated method stub
	if(tp==null) return null;
		return tp.external ? tree[tp.tree_index].getExternalNode(tp.node_index): tree[tp.tree_index].getInternalNode(tp.node_index);
	}
*/


public  Tree[] tree;
   
  private  static class TreePos{
	   public TreePos(int i, int j, boolean external) {
		   this.tree_index = i;
		   this.node_index = j;
		   this.external= external;
		// TODO Auto-generated constructor stub
	}
	   public String toString(){
		   return tree_index+","+node_index+","+external;
	   }
	   boolean external;
	int tree_index;
	   int node_index;
   }
   
   //this maps the slug to its tree and position in it.
  // private Map<String,TreePos > slugToPos = new HashMap<String, TreePos>();
   private Map<String,Node > slugToNode = new HashMap<String, Node>();
//   private Map<String,Node > shortSlugToNode = new HashMap<String, Node>();
  
   /* (non-Javadoc)
 * @see japsadev.bio.phylo.CommonTree#print(java.io.File)
 */
@Override
public  void print(File out) throws IOException{
	   PrintStream pw ;
	   if(out.getName().endsWith(".gz")){
		  pw = new PrintStream(new GZIPOutputStream(new FileOutputStream(out)));
	   }else{
		   pw = new PrintStream((new FileOutputStream(out)));
	   }
	//   PrintWriter pw = new PrintWriter(new FileWriter(out));
	 
	   for(int i=0; i<tree.length; i++){
		 Iterator<Node> n = NodeUtils.depthFirstIterator(tree[i].getRoot());  
		
		inner: for(int j=0; n.hasNext()  ;j++){
		//	 System.err.println(i+" "+j);
			 Node node = n.next();
			 Identifier id  = node.getIdentifier();
			 String nme =id.getName();
			
			 int level = ((Integer)id.getAttribute("level")).intValue();
			 String hex = ((String)id.getAttribute("css"));		
			 String alias = ((String)id.getAttribute("alias"));	
			 String alias1 = ((String)id.getAttribute("alias1"));	
			 String prefix = ((String)id.getAttribute("prefix"));		
			//System.err
			 pw.print(prefix+nme);
			 if(hex!=null) pw.print("\tcss="+hex);
			 if(alias!=null) pw.print("\talias="+alias);
			 if(alias1!=null) pw.print("\talias1="+alias1);
			 pw.println();
		 }
		 pw.println("------------------------------------");
	   }
	   
	   pw.close();
   }   
  


 
	private static final Pattern plusminus = Pattern.compile("[+|-][a-zA-Z\\[\\']");
	
	 private int getLevel(String nextLine){
		/*int a = nextLine.indexOf('-')+1;
		int b = nextLine.indexOf('+')+1;
		return Math.max(a, b);*/
		 Matcher matcher = plusminus.matcher(nextLine);
		 if(matcher.find())
		 	return  matcher.start()+1;
		else return 0;
		
	}
	
	//Map<String, String> alias = new HashMap<String, String>();
	
	 private Node createFromSpeciesFile(String[] line, String alias1, Node parent, int cnt, double bl){
		 String name = line[1];
		 String[] names = name.split("\\|");
		 
		 if(names.length>3 && names[3].startsWith("NC")) name = names[3];
		 Node n = new SimpleNode(name,bl);
		 Identifier id = n.getIdentifier();
		 Identifier pid = parent.getIdentifier();
		 String prefix = ((String)pid.getAttribute("prefix"));
		 if(parent.isRoot()) prefix = prefix+"+-";
		 id.setAttribute("level",((Integer) pid.getAttribute("level"))+2);
		 id.setAttribute("prefix", " | "+prefix);
		 id.setAttribute("alias", line[0]);
		 id.setAttribute("alias1", alias1);
		 id.setAttribute("speciesIndex", cnt);
		 String css =(String) pid.getAttribute("css");
		 if(css!=null){
			 id.setAttribute("css", css);
		 }
		 parent.addChild(n);
		 this.putSlug(n);
		 return n;
	 }
   private String collapse(String[] line, int start, int end, String string) {
	StringBuffer sb = new StringBuffer(line[start]);

	for(int i=start+1; i<end; i++){
		sb.append(string+line[i]);
	}
	return sb.toString();
}

   
 public boolean putSlug( Node n){
	  String name = n.getIdentifier().getName();
	  
		  
	   String slug = Slug.toSlug(name, slug_sep);
	   String shortSlug= Slug.toSlug(name,3, slug_sep);
	  // String shortSlug1= Slug.toSlug(name,1, slug_sep);
	   boolean contains = slugToNode.containsKey(slug);
	   boolean containsShort = slugToNode.containsKey(shortSlug);
	
	   if(!contains)  slugToNode.put(slug, n);
	   if(!containsShort)  slugToNode.put(shortSlug, n);
	   //if(!containsShort1)  slugToNode.put(shortSlug1, n);
		return contains;
   }
   
private Node make(String line_, int  level, Node parent){
	   String[] lines = line_.split("\t");
	   String line = lines[0];
	   String name = line;
	   String prefix = "";
	   if(level>=0) {
		   name = line.substring(level, line.length());
		   prefix = line.substring(0,level);
	   }
	   /*if(gid!=null){
		   //makes sure we use scientific name
			  String name1 = this.gid.getName(name);
			  if(name1!=null) name = name1;
		 }*/
	   Node n = new SimpleNode(name, 0.1);
	   n.getIdentifier().setAttribute("level",level);
	   n.getIdentifier().setAttribute("prefix",prefix);
	  for(int i=1; i<lines.length; i++){
		   String[] v = lines[i].split("=");
		   n.getIdentifier().setAttribute(v[0],v[1]);
	   }
	   putSlug(n);
	   if(parent!=null) parent.addChild(n);
	   return n;
   }
	
	
Node unclassified	;
	
protected NCBITree(File file) throws IOException {
	BufferedReader br;
	if(file.getName().endsWith(".gz")){
		br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file))));
	}
	else{
		br = new BufferedReader(new FileReader(file));
	}
	String nextLine = br.readLine();
	String[] nextLines = nextLine.split("\t");
	List<Node> roots = new ArrayList<Node>();
	//assume unclassified are first
	if(!nextLine.startsWith("unclassified")){
		unclassified = make("unclassified", 0, null);
		roots.add(unclassified);
	}
	
	outer: while(nextLine !=null){
		Node parent; 
		Node root;
		int parentlevel;
		
		root= make(nextLine, 0, null);
		if(root.getIdentifier().getName().equals("unclassified")) unclassified = root;
		roots.add(root);
		parent = root;
		parentlevel =0;
		inner: while((nextLine = br.readLine())!=null){
			nextLines = nextLine.split("\t");
			if(nextLine.startsWith("--")){
				nextLine=br.readLine();
				nextLines = nextLine==null ? null : nextLine.split("\t");
				continue outer;
			}
			
			int level = getLevel(nextLines[0]);
			//System.err.println(level+"->"+nextLine);
			while(level<=parentlevel){
				if(parent==root) {
					Node n = make(nextLine, 2, unclassified);
					//System.err.println("excluding  " +nextLine);
					continue inner;
				}
				parent = parent.getParent();
				parentlevel = ((Integer) parent.getIdentifier().getAttribute("level")).intValue();
			}
			Node n = make(nextLine, level, parent);
		
			
			//System.err.println(n+ "--->  "+parent);
			parentlevel = level;
			parent = n;
		}
		
		}

		
		
	
	for(int i=1; i<roots.size(); i++){
		Node root = roots.get(i);
		if(root.getIdentifier().getName().equals("unclassified")){
			throw new RuntimeException("unclassified should be first entry, if it exists");
		}
	}
	System.err.println("making trees");
		this.tree = new Tree[roots.size()];
		System.err.println(tree.length);
		System.err.println(this.slugToNode.size());
		for(int i=0; i<tree.length; i++){
			System.err.println(roots.get(i).getIdentifier().getName());
			tree[i] = new SimpleTree(roots.get(i));
			int cnt =tree[i].getInternalNodeCount();
			int cnt1 =tree[i].getExternalNodeCount();
			System.err.println(cnt+" "+cnt1);
			/*
			for(int j=cnt-1; j>=0; j--){
				String name = tree[i].getInternalNode(j).getIdentifier().getName();
				this.slugToPos.put(Slug.toSlug(name), new TreePos(i,j, false));
			}
		
			for(int j=0; j<cnt1; j++){
				String name = tree[i].getExternalNode(j).getIdentifier().getName();
				this.slugToPos.put(Slug.toSlug(name), new TreePos(i,j, true));
			}*/
			
		}
		
		br.close();
	}
	
	
	public void addSpeciesIndex(File speciesIndex) throws  IOException{
		if(speciesIndex!=null && speciesIndex.exists()){
			PrintWriter missing = new PrintWriter(new FileWriter("missing.txt"));
				 BufferedReader br1 = new BufferedReader(new FileReader(speciesIndex));
				 String st = "";
				for(int i=0; (st = br1.readLine())!=null; i++){
					updateTree(st, i, 0.0, missing);
				 }
				br1.close();
				missing.close();
				if(false){
				System.err.println("thinning");
				 for(int i=0; i<tree.length; i++ ){
					 Node root = tree[i].getRoot();
					 thinTree(root, 0);
				 }
				}
				 System.err.println("done");
				
		}
	}

	@Override
	public Tree[] getTrees() {
		return this.tree;
	}
	
	
	
	
	
	
}
