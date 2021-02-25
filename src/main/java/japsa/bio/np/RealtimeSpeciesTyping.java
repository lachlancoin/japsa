/*****************************************************************************
 * Copyright (c) Minh Duc Cao, Monash Uni & UQ, All rights reserved.         *
 *                                                                           *
 * Redistribution and use in source and binary forms, with or without        *
 * modification, are permitted provided that the following conditions        *
 * are met:                                                                  * 
 *                                                                           *
 * 1. Redistributions of source code must retain the above copyright notice, *
 *    this list of conditions and the following disclaimer.                  *
 * 2. Redistributions in binary form must reproduce the above copyright      *
 *    notice, this list of conditions and the following disclaimer in the    *
 *    documentation and/or other materials provided with the distribution.   *
 * 3. Neither the names of the institutions nor the names of the contributors*
 *    may be used to endorse or promote products derived from this software  *
 *    without specific prior written permission.                             *
 *                                                                           *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS   *
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR    *
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR         *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,     *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,       *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR        *
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF    *
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING      *
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS        *
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.              *
 ****************************************************************************/

/**************************     REVISION HISTORY    **************************
 * 07/09/2014 - Minh Duc Cao: Created                                        
 *  
 ****************************************************************************/

package japsa.bio.np;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.Stack;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import japsa.bio.phylo.NCBITree;
import japsa.seq.SequenceOutputStream;
import japsa.seq.SequenceReader;
import japsa.tools.seq.CachedFastqWriter;
import japsa.tools.seq.CachedSequenceOutputStream;
import japsa.util.DoubleArray;
import pal.misc.Identifier;
import pal.tree.Node;

/**
 * @author Minh Duc Cao, Son Hoang Nguyen, Lachlan Coin
 *
 */
public class RealtimeSpeciesTyping {
	public static String bases_covered = "bases_covered";
	public static String fraction_covered="fraction_covered";
	
	private static final Logger LOG = LoggerFactory.getLogger(RealtimeSpeciesTyping.class);
	
	public static boolean JSON = false;
	public static double ALPHA=0.05;
	public static int MIN_READS_COUNT=0;
	public static boolean writeSep = false;
	public static boolean writeUnmapped = false;
	private RealtimeSpeciesTyper typer;
	private OutputStream outputStream;
	//private BufferedReader indexBufferedReader;
	private HashSet<String> filterSet = new HashSet<String>();
	/**
	 * Minimum quality of alignment
	 */
	private double minQual = 1;
	private boolean twoDOnly = false;
	CachedFastqWriter fqw_unmapped = null;
	CachedFastqWriter fqw_filtered = null;
	final public String unmapped_reads;
	String indexFile;
	Integer currentReadCount = 0;
	Integer currentReadAligned = 0;
	Long currentBaseCount = 0L;
	 File fastqdir, outdir;
	File referenceFile;

	//seq ID to species name (from index ref file)
	HashMap<String, String> seq2Species = new HashMap<String, String>();
	//HashMap<String, SpeciesCount> species2Count = new HashMap<String, SpeciesCount>();
	ArrayList<String> speciesList = new ArrayList<String>(); 
	
	//to output binned sequences
	public static boolean OUTSEQ=false;
	HashMap<String, Coverage> species2ReadList = new HashMap<String, Coverage>();

	//** sorted based on coverage */
	static class Interval implements Comparable{
		int start,end, coverage;
		@Override
		public boolean equals(Object o){
			return start== ((Interval)o).start && end== ((Interval)o).end && coverage== ((Interval)o).coverage;  
		}
		public String toString(){
			return start+","+end+","+coverage;
		}
		public Interval(int start2, int end2, int i) {
			this.start = start2; this.end = end2; this.coverage = i;
		}
		
		public int overlap(Interval interval){
			int start2 = interval.start; int end2 = interval.end;
			int overl = Math.min(end -start2,end2 -start);
			int minlen = Math.min(end2 - start2, end-start);
			return Math.min(overl, minlen);
		}

		
		
		@Override
		public int compareTo(Object o) {
			return Integer.compare(coverage, ((Interval)o).coverage);
		}

		public Integer bases() {
			return end-start + 1;
		}
	}
	
	 
	/** this class represents the coverage of each species
	 * currently only for single chrom species, otherwise each chrom is considered its own species
	 *  */
	class Coverage{
		
		Coverage(String species,  Node node, File fastqdir, boolean writeSep1){
			this.species = species;
			this.node = node;
			
			fqw = writeSep1 ? new CachedSequenceOutputStream(fastqdir, species, true) : null;
			//	new CachedFastqWriter(outdir, species);
			//
		}
		
		Node node; // this is the node in the tree.  We dont initialise this until there are at least one read
		String species;
	//	int len;
		Interval[] newi = new Interval[3];
		SortedMap<Integer, Integer> mapq = new TreeMap<Integer, Integer>();
		SortedMap<Integer, Integer> mapAlign = new TreeMap<Integer, Integer>();
		SortedMap<Integer, Integer> mapLen = new TreeMap<Integer, Integer>();

		int readCount=0; int baseCount=0;
		// we keep the intervals non nested
		List<SortedMap<Integer,Interval>> coverages = new ArrayList<SortedMap<Integer, Interval>>();
		List<String> contig_names = new ArrayList<String>();
		//SortedMap<Integer, Interval> coverage = new TreeMap<Integer, Interval>(); //based on start
		
		CachedSequenceOutputStream fqw= null;
		boolean lock = false;
		
		public void updateNodeAndParents(){
			if(node!=null){
				Integer[] cnts = (Integer[]) this.node.getIdentifier().getAttribute(NCBITree.count_tag);
				cnts[0]=readCount;
				Integer[] cnts1 = (Integer[]) this.node.getIdentifier().getAttribute(NCBITree.count_tag1);
				cnts1[0] = readCount;
				Node parent = node.getParent();
				while(parent!=null ){
				//adds counts to the tree
					Integer[] cnts1_p = (Integer[]) parent.getIdentifier().getAttribute(NCBITree.count_tag);
					cnts1_p[0] = cnts1_p[0] + readCount;
					parent = parent.getParent();
				}
			}
		}
		public void medianQ(double[] percentiles, double[] vals, SortedMap<Integer, Integer>map){
			double cumul0=0;
			double cumul1 =0;
			double total = map.values().stream().mapToInt(Integer::intValue).sum();
			 int mapq0=0;
			Iterator<Integer> it = map.keySet().iterator();
			while(it.hasNext()){
				Integer key = it.next();
				Integer cnt = map.get(key);
				int mapq1= key;
				cumul1 = cumul0+cnt;
				double perc0 = cumul0/total;
				double perc = cumul1/total;
				for(int j=0; j<percentiles.length; j++){
					if(percentiles[j] >= perc0  && percentiles[j] <=perc){
						vals[j]  = interpolate(mapq0, mapq1, perc0, perc, percentiles[j]);
					}
				}
				mapq0 = mapq1;
				cumul0 = cumul1;
			}
		}
		
		public  synchronized  Double[]  medianReadCoverage(double[] percentiles, double[] vals, int index){
			SortedMap<Integer, Interval> coverage = this.coverages.get(index);
			int non_zero_bases=0;
			Integer len = species2Len.get(this.contig_names.get(index));// node==null ? null : (Integer) node.getIdentifier().getAttribute("length");
			while(lock){
				try{
					LOG.debug("waiting on lock 1");
				Thread.sleep(100);
				}catch(InterruptedException exc){
					exc.printStackTrace();
				}
			}
			lock=true;
			// this is a hack, we should get the actual length for this species
			int zeroBases=0;
			int prev =1;
			for(Iterator<Interval> it = coverage.values().iterator(); it.hasNext();){
				Interval nxt = it.next();
				int gap = nxt.start - prev;
				zeroBases+=gap;
				prev = nxt.end;
			}
			if(len!=null && len> prev) zeroBases+=len -prev;
			else len = prev;
			List<Interval> l = new ArrayList<Interval> ();
			l.addAll(coverage.values());
			Collections.sort(l);
			lock = false;
			
			
			double cumul = zeroBases;
		  //  SortedMap<Integer, Double> covs = new TreeMap<Integer, Double>();
		//(covs!=null) covs.put(0, (double) zeroBases);
			for(int j=0; j<percentiles.length; j++){
				if(percentiles[j] <=(double) zeroBases/(double) len){
					vals[j]  =0; // more zero bases than the percentile
				}
			}
			int cov0 =0;
			double cumul0 = cumul;;
			
			for(int i=0; i<l.size(); i++){
				Interval li = l.get(i);
				int cov = li.coverage;
				non_zero_bases +=li.bases();
				cumul = cumul0 +li.bases(); //percentage of bases
			//	if(covs!=null) covs.put(cov, cumul);
				double perc0 = cumul0 / (double) len;
				double perc = cumul / (double) len;
				//System.err.println(perc0+","+perc);
				for(int j=0; j<percentiles.length; j++){
					if(percentiles[j] >= perc0  && percentiles[j] <=perc){
						vals[j]  = interpolate(cov0, cov, perc0, perc, percentiles[j]);
					}
				}
				cov0 = cov;
				cumul0 = cumul;
			}
			if(node!=null){
				Identifier id = this.node.getIdentifier();
				id.setAttribute(bases_covered, non_zero_bases);
				id.setAttribute(fraction_covered, (double) non_zero_bases/(double)len);
			}
			return new Double[] {(double)non_zero_bases, (double) non_zero_bases/(double)len, (double) len};
		//	System.err.println("h");
		}
		
		
		private double interpolate(int cov0, int cov, double perc0, double perc, double d) {
			// TODO Auto-generated method stub
			return (double) cov0 + ((d-perc0)/ (perc-perc0)) * ((double)cov-(double) cov0);
		}

		
		//extra are the extra bits still needed to be analysed (they may overlap elsewhere)
		// tomod are the bits which can be added directly.
		public void split(Interval i_new,Interval i_existing, List<Interval> tomod, Stack<Interval> extra_left, Stack<Interval> extra_right){
			boolean isLeft = i_new.start < i_existing.start ; // is new to left of existing based on start position
			Interval left = isLeft ? i_new: i_existing;
			Interval right =isLeft ? i_existing: i_new;
			boolean nested = right.end <=left.end;
			Interval i1,i2,i3;
			if(nested){ //right nested inside left
				// this replaces left.start and right.start
				i1 = (new Interval(left.start, right.start-1, left.coverage)); //left
				i2 = (new Interval(right.start, right.end, left.coverage + right.coverage)); //middle
				i3 = (new Interval(right.end+1, left.end,   left.coverage)); //right
				tomod.add(i2);
				if(isLeft){
					extra_left.push(i1); extra_right.push(i2); 
				}else{
					tomod.add(i1); tomod.add(i2);
				}
			}else{
				i1 = (new Interval(left.start, right.start-1, left.coverage)); //left
				i2 = (new Interval(right.start, left.end, left.coverage + right.coverage)); //middle
				i3= (new Interval(left.end+1,right.end,   right.coverage)); //right
				tomod.add(i2);
				tomod.add(isLeft ? i3 : i1);
				if(isLeft){
					extra_right.push(i3);
				}else{
					extra_left.push(i1);
				}
			}
			
		}
		
		public void addRead(SAMRecord sam){
			
				//if(node==null) node = tree.getNode(species);
			if(fqw!=null){	
				sam.setReadName(sam.getReadName()+" "+sam.getReferenceName()+":"+sam.getAlignmentStart()+"-"+sam.getAlignmentEnd()+" "+sam.getMappingQuality());
				
				fqw.write(sam);
			}
			if(sam.getReadString().equals("*")){
				int st = sam.getAlignmentStart();
				int end = sam.getAlignmentEnd();
				int len = sam.getReadLength();
				byte[] bases = sam.getReadBases();
				System.err.println(sam.getReadName());
				System.err.println(sam.getReadString());
				System.err.println(st);
				System.err.println(end);
				System.err.println(len);
				System.err.println(sam.isSecondaryOrSupplementary());
				System.exit(0);
				throw new RuntimeException("!!");
			}
			
			this.addInterval(sam.getReferenceName(),sam.getAlignmentStart(), sam.getAlignmentEnd());
			//System.err.println(this.species);
			int q = sam.getMappingQuality();
			int alignL = (sam.getAlignmentEnd()-sam.getAlignmentStart());
			int readLength = (int) Math.round((double) sam.getReadLength()/10.0) * 10;
			int alignF = 10*(int) Math.round(10*(double)alignL/(double)sam.getReadLength());
			Integer cnt = mapq.get(q);
			Integer cnt1 = mapLen.get(readLength);
			Integer cnt2 = mapAlign.get(alignF);
			this.mapq.put(q, cnt==null ? 1 : cnt+1);
			this.mapLen.put(readLength, cnt1==null ? 1: cnt1+1);
			this.mapAlign.put(alignF, cnt2==null ? 1: cnt2+1);

			this.baseCount = this.baseCount+ sam.getAlignmentEnd() - sam.getAlignmentStart();
			this.readCount++;
		}
	
		Interval firstOverlap(Interval i, int start_pos, int index){
			SortedMap<Integer, Interval> coverage = this.coverages.get(index);
			for(Iterator<Interval> it = coverage.tailMap(start_pos).values().iterator(); it.hasNext();){
				Interval nxt = it.next();
				if(i.overlap(nxt)>0) return nxt;
			}
			return null;
		}
		public String toString(){
			return this.species+"_"+coverages;//+":"+coverages..values().toString();
		}
		//we assuming existing segments non overlapping
		public void addInterval(String refname, int start, int end){
			int index = this.contig_names.indexOf(refname);
			if(index<0){
				index = contig_names.size();
				contig_names.add(refname);
				this.coverages.add(new TreeMap<Integer, Interval>());
			}
			SortedMap<Integer, Interval> coverage = this.coverages.get(index);

			while(lock){
				try{
					LOG.debug("waiting on lock");
					Thread.sleep(100);
				}catch(InterruptedException exc){
					exc.printStackTrace();
				}
			}
			lock=true;
			List<Interval> toadd = new ArrayList<Interval>();
			Stack<Interval> extra_left = new Stack<Interval>(); // extra are like the leftover after merging
			Stack<Interval> extra_right = new Stack<Interval>();
			extra_right.push(new Interval(start, end,1));
			
//			System.err.println("add "+species+" "+i2 + this.coverage.size());
			int start_pos =0;
			while(extra_right.size()>0){ // extra left should all be non overlapping segments as we starting search from left
				Interval i_new = extra_right.pop();
				Interval i_existing = firstOverlap(i_new, start_pos, index);
				if(i_existing!=null){
					split(i_new, i_existing, toadd, extra_left, extra_right);
					start_pos = i_existing.end+1; 
				}else{
					toadd.add(i_new); // no overlaps, can just add segment
				}
			}
			toadd.addAll(extra_left); 
			// we dont need to remove original entries because we will replace them in this step
			for(Iterator<Interval> it = toadd.iterator(); it.hasNext();){
				Interval i1 = it.next();
				coverage.put(i1.start, i1);
			}
			lock = false;
			//System.err.println("species: "+this.toString());
			
		}
		public void close(){
			if(fqw!=null) this.fqw.close();
		}
		public double readCount() {
			
			return readCount;
		}
	}
	
	public RealtimeSpeciesTyping(File outdir, String indexFile) throws IOException{
		this.outdir = outdir;
		this.indexFile = indexFile;
		this.fastqdir= new File(outdir,"fastqs"); fastqdir.mkdir();
		this.unmapped_reads = (new File(outdir, "unmapped")).getAbsolutePath();
		if(writeUnmapped){
		this.fqw_unmapped = new CachedFastqWriter(outdir, "unmapped", false);
		}
		this.fqw_filtered = null;//new CachedFastqWriter(outdir, "filtered");

	}
	NCBITree tree;
	
	//* referenceFile is to get the length map */
	public RealtimeSpeciesTyping(String indexFile, String treeFile, String outputFile, File outdir, File referenceFile) throws IOException{
		this(outdir, indexFile);
		this.referenceFile = referenceFile;
		tree = new NCBITree(new File(treeFile), false);
	
		this.outputStream = SequenceOutputStream.makeOutputStream(outdir.getAbsolutePath()+"/"+outputFile);
		
		//this.indexBufferedReader = SequenceReader.openFile(indexFile);
		typer = new RealtimeSpeciesTyper(this,outputStream);
		preTyping();
	
	}

	public RealtimeSpeciesTyping(String indexFile, OutputStream outputStream, File outdir) throws IOException {
		this(outdir, indexFile);
		LOG.debug("string outputstream");
	//	this.indexBufferedReader = SequenceReader.openFile(indexFile);
		this.outputStream = outputStream;
		typer = new RealtimeSpeciesTyper(this, outputStream);
		preTyping();
	}

	/*public RealtimeSpeciesTyping(BufferedReader indexBufferedReader, String outputFile, File outdir) throws IOException {
		this(outdir);
		LOG.debug("bufferedreader string");

		//this.indexBufferedReader = indexBufferedReader;
		this.outputStream = SequenceOutputStream.makeOutputStream(outputFile);
		typer = new RealtimeSpeciesTyper(this, outputStream);
		preTyping();
	}

	public RealtimeSpeciesTyping(BufferedReader indexBufferedReader, OutputStream outputStream, File outdir) throws IOException {
		this(outdir);
		LOG.debug("bufferedreader outputstream");

	//	this.indexBufferedReader = indexBufferedReader;
		this.outputStream = outputStream;
		typer = new RealtimeSpeciesTyper(this, outputStream);
		preTyping();
	}*/

//	static class SpeciesCount implements Comparable<SpeciesCount> {
//		String species;
//		int count = 0;
//
//		SpeciesCount (String s){
//			species = s;
//		}
//
//		/* (non-Javadoc)
//		 * @see java.lang.Comparable#compareTo(java.lang.Object)
//		 */
//		@Override
//		public int compareTo(SpeciesCount o) {		
//			return o.count - count;
//		}
//
//	}
public static void readSpeciesIndex(String indexFile, Map<String, String> seq2Species, boolean splitPlasmids)throws IOException{
	BufferedReader indexBufferedReader = SequenceReader.openFile(indexFile);
	String line = "";
	while ( (line = indexBufferedReader.readLine())!=null){
		if (line.startsWith("#"))
			continue;


		String sp=null,seq=null;
			
		String [] toks = line.split(">");
		if(toks.length < 2){
			LOG.info("Illegal speciesIndex file!");
			System.exit(1);
		}
			boolean plasmid = splitPlasmids && toks[1].indexOf("plasmid")>=0;
		sp=toks[0].trim();
		if(plasmid)sp = sp+".plasmid";
		seq=toks[1].split("\\s+")[0];

		if (seq2Species.put(seq, sp) != null)
			throw new RuntimeException("sequence " + seq +" presents multiple time");
//		else
//			LOG.info("==>adding " + seq + " to " + sp);
		
				
	}//while
}

HashMap<String, Integer> species2Len = new HashMap<String, Integer>();
public static List<String> speciesToIgnore = null;
	private void preTyping() throws IOException{
		readSpeciesIndex(indexFile, this.seq2Species, true);
		Iterator<String> it = seq2Species.values().iterator();
		while(it.hasNext()){
			String sp = it.next();
			boolean writeSep1 = writeSep && (speciesToIgnore==null || ! speciesToIgnore.contains(sp));
			if (species2ReadList.get(sp) == null){
//				LOG.info("add species: "+sp);
				Node n =tree==null ? null : tree.getNode(sp);
				if(n==null){
					LOG.warn("node is null "+sp);
				}
			//	System.err.println(sp);
				species2ReadList.put(sp,new Coverage(sp,n, 	fastqdir, writeSep1));			
						
			}	
		}
		tree.annotateWithGenomeLength(referenceFile, seq2Species, species2Len);
		//indexBufferedReader.close();
	
		LOG.info(seq2Species.size() + "   " + species2ReadList.size());
		speciesList.addAll(species2ReadList.keySet());

		//Write header				
	}

	/**
	 * @param minQual the minQual to set
	 */
	public void setMinQual(double minQual) {
		this.minQual = minQual;
	}

	/**
	 * @param twoOnly the twoOnly to set
	 */
	public void setTwoOnly(boolean twoOnly) {
		this.twoDOnly = twoOnly;
	}

	public void typing(String bamFile, int readNumber, int timeNumber) throws IOException, InterruptedException {
		InputStream bamInputStream;

		if ("-".equals(bamFile))
			bamInputStream = System.in;
		else
			bamInputStream = new FileInputStream(bamFile);

		typing(bamInputStream, readNumber, timeNumber);
	}

	/**\
	 * @param filter the species keywords list (separated by comma) to excluded
	 */
	public void setFilter(String filter) {
		if(!filter.isEmpty()){
			String[] toks = filter.split(";");
			for(String tok:toks)
				if(!tok.isEmpty()){
					filterSet.add(tok);
				}
		}
		
	}
	public void typing(InputStream bamInputStream, int readNumber, int timeNumber) throws IOException, InterruptedException{
		SamReaderFactory.setDefaultValidationStringency(ValidationStringency.SILENT);
		SamReader samReader = SamReaderFactory.makeDefault().open(SamInputResource.of(bamInputStream));
		SAMRecordIterator samIter = samReader.iterator();
		typing(samIter, readNumber, timeNumber);
		samReader.close();
	}
	boolean realtimeAnalysis = false;
	public void typing(Iterator<SAMRecord> samIter, int readNumber, int timeNumber) throws IOException, InterruptedException{
		//if (readNumber <= 0)
		//	readNumber = 1;			
		typer.setReadPeriod(readNumber);
		typer.setTimePeriod(timeNumber * 1000);
		LOG.info("Species typing ready at " + new Date());
		String readName = "", refName = "";

		
		if(realtimeAnalysis){
			Thread thread = new Thread(typer);
			LOG.info("starting RealtimeSpeciesTyper thread");
			thread.start();
			LOG.info("started  RealtimeSpeciesTyper thread");
		}
		HashSet<String> skipList = new HashSet<>();
		while (samIter.hasNext()){
//			try{
			SAMRecord sam = samIter.next();
		
			if(sam==null) {
				System.err.println("warning sam record is null");
				break;
			}
			if(sam.isSecondaryOrSupplementary()){
				continue;
			}
	
			if (this.twoDOnly && !sam.getReadName().contains("twodim")){
				continue;
			}

			if (!sam.getReadName().equals(readName)){
				readName = sam.getReadName();
				synchronized(this){
					currentReadCount ++;
					currentBaseCount += sam.getReadLength();
				}
			} 
			
			if (sam.getReadUnmappedFlag()){
				LOG.debug("failed unmapped check");
				if(fqw_unmapped!=null) this.fqw_unmapped.write(sam);
				continue;			
			}

			if (sam.getMappingQuality() < this.minQual) {
				LOG.debug("failed minQual check");
				if(!sam.isSecondaryOrSupplementary()){
					if(fqw_unmapped!=null) this.fqw_unmapped.write(sam);
				}
				continue;
			}
			

			if(skipList.contains(readName)){
				
				LOG.debug("filter {}", readName);
				continue;
			}
			
			 refName = sam.getReferenceName();
			if(filterSet.contains(seq2Species.get(refName))){
			
				if(!sam.isSecondaryOrSupplementary()){
					skipList.add(readName);
					if(fqw_filtered!=null) this.fqw_filtered.write(sam);
				}
				continue;
			}
			
		
			/* this probably not necessary 
			if(sam.getReadNegativeStrandFlag()){
				//System.err.println("switching strand");
				SequenceUtils.flip(sam, false); // switch read but keep flag. This corrects minimaps correction when it maps
			}*/
			
			String species = seq2Species.get(refName);
			if (species == null){
				throw new RuntimeException(" Can't find species with ref " + refName + " line " + currentReadCount );
			}

			//SpeciesCount sCount = species2Count.get(species);
			Coverage readList = species2ReadList.get(species);
			if (readList == null){
				throw new RuntimeException(" Can't find record with species " + species + " line " + currentReadCount );
			}
			//if(readList.readList.size()==0 || !readList.readList.contains(readName))
				synchronized(this) {
					currentReadAligned ++;	
					/*if(tree!=null){
						tree.addRead(this.seq2Species.get(sam.getReferenceName()));
					}*/
					readList.addRead(sam);
					//readList.add(readName);
	
				}
//			}catch(Exception exc){
//				exc.printStackTrace();
//			}
		}//while

		//final run
		//typer.simpleAnalysisCurrent();
		if(fqw_filtered!=null) this.fqw_filtered.close();
		if(fqw_unmapped!=null) this.fqw_unmapped.close();
		typer.stopWaiting();//Tell typer to stop
		if(!realtimeAnalysis){
			typer.run();
		}
		//samIter.close();
		
	}	

	public static class RealtimeSpeciesTyper extends RealtimeAnalysis {
		MultinomialCI rengine;
		RealtimeSpeciesTyping typing;
		public SequenceOutputStream countsOS;
		File krakenResults; //kraken formatted results


		public RealtimeSpeciesTyper(RealtimeSpeciesTyping t, OutputStream outputStream) throws IOException {
			typing = t;
			krakenResults = new File(t.outdir,"results.krkn");
			rengine = new MultinomialCI(ALPHA);

			countsOS = new SequenceOutputStream(outputStream);
			if(!JSON)
				countsOS.print("time\tstep\treads\tbases\tspecies\tprob\terr\ttAligned\tsAligned\tbases_covered\tfraction_covered\tlength_best_contig\tcoverage_percentiles\tmapQ\tlength\talignFrac\tprop_to_most_cov_contig\thighest_cov_contig\n");
		}
		double[] perc = new double[] {0.5};// 0.5, 0.75, 0.9, 0.95, 0.99}; // percentiles for printing median
		double[] percQ = new double[] {0.5};
		double[] percL = new double[] {0.5};
		DoubleArray countArray = new DoubleArray();
		List<String> medianArray = new ArrayList<String>();
		ArrayList<String> speciesArray = new ArrayList<String> ();
		double[][] results = null;
		int[] order = null;
		public boolean lock = false;
		Long step;
		
		private void simpleAnalysisCurrent()  {
			lock = true; // so that the datastructure doesnt change while we doing calculation 
			double[] vals = new double[perc.length];
			double[] valsQ = new double[percQ.length];
			double[] valsL = new double[percL.length];
			//long step = lastTime;

			//Date date = new Date(lastTime);
			step = (lastTime - startTime)/1000;//convert to second

			int sum = 0;
			double [] count = new double[typing.speciesList.size()];
			for (int i = 0; i < count.length;i++){
				String spec_name = typing.speciesList.get(i);
				Coverage cov = typing.species2ReadList.get(typing.speciesList.get(i));
				count[i] = cov.readCount();		
				sum += count[i];
				
				
			}
			countArray.clear();medianArray.clear();speciesArray.clear();

			int minCount = MIN_READS_COUNT>0?MIN_READS_COUNT:Math.max(1,sum/1000);

			for (int i = 0; i < count.length;i++){			
				if (count[i] >= minCount){
					countArray.add(count[i]);
					String spec_name = typing.speciesList.get(i);
					speciesArray.add(spec_name);
					LOG.info(step+" : " + spec_name+ " == " + count[i]);
					//if(count[i]>0){
						Coverage cov = typing.species2ReadList.get(spec_name);
						
						 //SortedMap<Integer, Double>  covHist = 
						double max =0; int maxj=0; double tot_bases=0;
						double max1=0; int maxj_1 =0; 
						for(int j =0; j<cov.contig_names.size(); j++){
						  Double[] stats = cov.medianReadCoverage(new double[0], new double[0], j);
						  tot_bases+= stats[0];
						  if(stats[0] >max){
							  max = stats[0];
							  maxj=j;
						  }
						  if(stats[1] > max1){
							  max1 = stats[1];
							  maxj_1 =j;
						  }
						}
						Double proportion = max/ tot_bases;
						String nme = cov.contig_names.get(maxj);
						String nme1 = cov.contig_names.get(maxj_1);
						Double[] stats =  cov.medianReadCoverage(perc, vals,maxj);
						 String st = combine(perc,vals);
						 cov.medianQ(percQ, valsQ,cov.mapq);
						 cov.medianQ(percL, valsL, cov.mapLen);
						 String st1 = combine(percQ,valsQ);
						 String st2 = combine(percL,valsL);
						 cov.medianQ(percL, valsL, cov.mapAlign);
						 String st3 = combine(percL,valsL);
						 medianArray.add(String.format("%5.3g", stats[0]).trim()+"\t"+String.format("%5.3g",stats[1]).trim()+"\t"+String.format("%5.3g",stats[2]).trim()
						 +"\t"+st+"\t"+st1+"\t"+st2+"\t"+st3
								 +"\t"+nme+":"+String.format("%5.3g",proportion).trim()+"\t"+nme1+":"+max1);
				/*	if(count[i]>10 * vals[vals.length-1]){
						System.err.println(count[i]+" vs "+st);
						if(covHist!=null) System.err.println(covHist);
					}*/
					LOG.info("medians "+step+" : "+spec_name+" "+ st+ " vs count "+count[i]);
					//}
				}
			}		
			//if (countArray.size() > 10) return;
			countArray.add(1); medianArray.add("--");
			speciesArray.add("others");		

			lock =false;
			
			rengine.assignCount(countArray.toArray());
			rengine.eval();        
			//REXP tab  = rengine.eval("tab",true);  
			results=rengine.tab();
			this.order = rengine.rank();
		}
		@Override
		protected void writeFinalResults() {
			try{
			typing.tree.zeroCounts(0, 1);; // add arrays to nodes for recording counts, or reset to zero
			Iterator<Coverage> it = typing.species2ReadList.values().iterator();
			while(it.hasNext()){
				Coverage cov = it.next();
				if(cov.readCount()> 0){		
					cov.updateNodeAndParents();
				}
				
			}
			typing.tree.trim(1e-7);
			typing.tree.print(this.krakenResults, new String[]{NCBITree.count_tag,NCBITree.count_tag1}, new String[] {"%d","%d"}, true);
			}catch(Exception exc){
				exc.printStackTrace();
			}
			
		}
		public static void sort(double[][] results, int i, int[] order){
			
		}
		private void writeResults(double min_thresh ) throws IOException {
		
			Gson gson = new GsonBuilder().serializeNulls().create();
			List<JsonObject> data = new ArrayList<JsonObject>();
		
			for (int i1 = 0; i1 < order.length;i1++){
				int i = order[i1];
				if (results[i][0] <= min_thresh)
					continue;

				Double mid = (results[i][0] + results[i][1])/2;
				Double err = mid - results[i][0];
				if(!JSON) {
					countsOS.print(timeNow + "\t" + step + "\t" + lastReadNumber + "\t" + typing.currentBaseCount + "\t" + speciesArray.get(i).replaceAll("_", " ") + "\t" + mid + "\t" + err + "\t" + typing.currentReadAligned + "\t" + countArray.get(i)+"\t"+medianArray.get(i));
					countsOS.println();
				}
				else {
					JsonObject jo = new JsonObject();
					jo.addProperty("species", speciesArray.get(i).replaceAll("_", " "));
					jo.addProperty("step", step.toString());
					jo.addProperty("reads", lastReadNumber.toString());
					jo.addProperty("bases", typing.currentBaseCount.toString());
					jo.addProperty("prob", mid.toString());
					jo.addProperty("err", err.toString());
					jo.addProperty("tAligned", typing.currentReadAligned.toString());
					jo.addProperty("sAligned", Double.valueOf(countArray.get(i)).toString());
					data.add(jo);

				}
			}

			if(JSON) {
				countsOS.print(gson.toJson(ImmutableMap.of(
						"timestamp", timeNow.toString(),
						"data", data
				)));
				countsOS.println();

			}
			countsOS.flush();
			LOG.info(step+"  " + countArray.size());
		}

		private String combine(double[] perc2, double[] vals) {
			StringBuffer sb = new StringBuffer();
			for(int i=0; i<perc2.length; i++){
				sb.append(String.format("%5.3g=%5.3g", new Double[] {perc2[i], vals[i]}).replaceAll(" ", "")+","); //perc2[i]+"="+vals[i]+" ");
			}
			return sb.toString().trim();
		}

		protected void close(){
			try{
				//rengine.end();
				countsOS.close();
			}catch (Exception e){
				e.printStackTrace();
			}
			
			Iterator<Coverage> it = this.typing.species2ReadList.values().iterator();
			while(it.hasNext()){
				it.next().close();
			}
			//print out
			
		}

		/* (non-Javadoc)
		 * @see japsa.bio.np.RealtimeAnalysis#analysis()
		 */
		@Override
		protected void analysis(){
			simpleAnalysisCurrent();
			try{
				
				this.writeResults( 0.000001);
			}catch (IOException e){
				LOG.warn(e.getMessage());
			}
		}
		@Override
		protected void lastAnalysis(){
			simpleAnalysisCurrent();
			try{
				
				this.writeResults(-1);//to write all results
			}catch (IOException e){
				LOG.warn(e.getMessage());
			}
		}

		/* (non-Javadoc)
		 * @see japsa.bio.np.RealtimeAnalysis#getCurrentRead()
		 */
		@Override
		protected int getCurrentRead() {
			return typing.currentReadCount;
		}

		
	}

}
