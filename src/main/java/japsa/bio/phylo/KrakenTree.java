package japsa.bio.phylo;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Stack;
import java.util.regex.Pattern;

import pal.misc.Identifier;
import pal.tree.Node;
import pal.tree.NodeUtils;

/** written by Lachlan Coin to parse txt files from commontree */
public  class KrakenTree extends NCBITree {


	
	
	void modAll(int i, int len) {
		String[] tags = new String[] {count_tag, count_tag1};
		for(int k=0; k<roots.size(); k++){
			Node root = roots.get(k);
			//if(root.getChildCount()>0){
				Iterator<Node> it = NodeUtils.preOrderIterator(root);
				while(it.hasNext()){
					Identifier id = it.next().getIdentifier();
					for(int j=0; j<tags.length; j++)
					{
						String count_tag2 = tags[j];
						Integer cnt = (Integer) id.getAttribute(count_tag2);
						if(cnt==null) cnt=0;
						int[] v = new int[len];
						v[i] = cnt;
						id.setAttribute(count_tag2, v);
					}
				}
			//}
		}
		
	}
	public KrakenTree(File file) throws IOException {
		super(file, true, true);
		// TODO Auto-generated constructor stub
	}
	
	public String get(Node n, int target_level, String attr){
		 Integer level = ((Integer)n.getIdentifier().getAttribute("level")).intValue();
			 while(level>target_level){
				 n  = n.getParent();
				 if(n==null) return "NA";
				 level = ((Integer)n.getIdentifier().getAttribute("level")).intValue();
			 }
			 if(level==target_level) return n.getIdentifier().getAttribute(attr).toString();
			 else return "NA";
	}
	
	
	static double bl = 0.04;
	@Override
	public void print(Node node, PrintStream pw, String count_tag){
		 Identifier id  = node.getIdentifier();
		 String nme =id.getName();
		 int[] counts = (int[]) id.getAttribute(count_tag);
		 Integer level = (int) Math.round((((Integer)id.getAttribute("level")).doubleValue()+1.0)/2.0);
		 if(node.isRoot()) level =0;
		String height = String.format("%5.3g", NodeUtils.getMinimumPathLengthLengthToLeaf(node)/bl).trim();
		 String hex = ((String)id.getAttribute("css"));		
		 String alias = ((String)id.getAttribute("alias"));	
		 String alias1 = ((String)id.getAttribute("alias1"));	
		 String prefix = ((String)id.getAttribute("prefix"));	
		Integer taxon = ((Integer)id.getAttribute("taxon"));
		double[] cssvals = (double[])id.getAttribute("cssvals");
		String[] cssvals1 = new String[cssvals.length];
		for(int i=0; i<cssvals.length; i++) cssvals1[i] = String.format("%5.3g",cssvals[i]).trim();
		StringBuffer sb = new StringBuffer();
		StringBuffer sb1 = new StringBuffer();
		Stack<String> l = new Stack<String>();
		Stack<String> l1 = new Stack<String>();
		if(!node.isRoot()){
			Node parent = node.getParent();
			while(!parent.isRoot()){
				l.push(parent.getIdentifier().getName());
				l1.push(parent.getIdentifier().getAttribute("taxon")+"");
				//l.push(p)
				//sb.append("->"+parent.getIdentifier().getName());
				parent = parent.getParent();
			}
			while(l.size()>0){
				sb.append(l.pop());
				sb1.append(l1.pop());
				if(l.size()>0){
					sb.append("->");
					sb1.append(",");
				}
			}
		}
		
		 //double height = node.getNodeHeight();
	//	 if(hex!=null) pw.print("\tcss="+hex);
		// if(alias!=null) pw.print("\talias="+alias);
		// if(alias1!=null) pw.print("\talias1="+alias1);
		//root    css=#000000ff   taxon=1 height=1.24
		//			header.append("name\tcolor\ttaxon\theight\tparents\ttaxon1\ttaxon2\ttaxon3\ttaxon4\ttaxon5");
		//header.append("name\tcolor\ttaxon\theight\tlevel\tcssvals\tparents\ttaxon1\ttaxon2\ttaxon3\ttaxon4\ttaxon5");

		 pw.print(nme+"\t"+hex+"\t"+taxon+"\t"+height+"\t"+level+"\t"+Arrays.asList(cssvals1)+"\t"+sb.toString()+"\t"+sb1.toString());
		 if(counts!=null){
			 for(int i=0; i<counts.length; i++){
				 pw.print("\t"+counts[i]);
			 }
		 }
		 //if(true) pw.print("\theight="+String.format("%5.3g", height).trim());

		 pw.println();
	}
	
	static Pattern p = Pattern.compile("[a-zA-Z]");
	 int getLevel(String line){
		 for(int i=0; i<line.length(); i++){
			 if(line.charAt(i)!=' ') {
				 return i-1;
			 }
		 }
		 return -1;
		 /*
		boolean mat =  Pattern.matches("cell", line);
		 Matcher m = p.matcher(line);
		 if(m.matches()){
			 m.find();
			 int st = m.start();
			 return st-1;
		 }else{
			 return -1;
		 }*/
	 }
	
	
	
	
}
