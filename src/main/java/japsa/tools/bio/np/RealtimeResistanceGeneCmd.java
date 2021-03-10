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

/*****************************************************************************
 *                           Revision History                                
 * 7 Aug 2015 - Minh Duc Cao: Created                                        
 * 
 ****************************************************************************/
package japsa.tools.bio.np;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import japsa.bio.np.RealtimeResistanceGene;
import japsa.bio.np.RealtimeSpeciesTyping;
import japsa.tools.bio.np.RealtimeSpeciesTypingCmd.ReferenceDB;
import japsa.tools.seq.CachedOutput;
import japsa.tools.seq.SequenceUtils;
import japsa.util.CommandLine;
import japsa.util.deploy.Deployable;

/**
 * @author minhduc
 *
 */
@Deployable(
	scriptName = "jsa.np.rtResistGenes", 
	scriptDesc = "Realtime identification of antibiotic resistance genes from Nanopore sequencing",
	seeAlso = "jsa.np.npreader, jsa.np.rtSpeciesTyping, jsa.np.rtStrainTyping, jsa.util.streamServer, jsa.util.streamClient"
	)
public class RealtimeResistanceGeneCmd extends CommandLine{	
	public RealtimeResistanceGeneCmd(){
		super();
		Deployable annotation = getClass().getAnnotation(Deployable.class);		
		setUsage(annotation.scriptName() + " [options]");
		setDesc(annotation.scriptDesc());

		addString("writeSep" , null, "strings to match for what to write fastq file out, which can be colon separated, e.g. plasmid:phage or all");
		addInt("minCountResistance", 2, "Mininum number of mapped reads for a species to be considered (for species typing step)");
		addInt("minCountSpecies", 2, "Mininum number of mapped reads for a species to be considered (for species typing step)");

		addString("output", "output.dat",  "Output file");
		addString("bamFile", null,  "The bam file");
		addString("fastqFile", null,  "fastq input");
		addBoolean("runKAlign", false, "whether to run msa to get high confidence calls");
		addDouble("score", 0.0001,  "The alignment score threshold");
		addString("msa", "kalign",
			"Name of the msa method, support poa, kalign, muscle and clustalo");

		addString("tmp", "_tmpt",  "Temporary folder");				
		addString("resDB", null,  "Path to resistance database", true);
		addString("dbs", null,  "For subsequent species typing", false);
		addString("dbPath",null, "path to databases",false);
		addString("resdir", "japsa_resistance_typing", "Results directory");
		addDouble("qual", 0,  "Minimum alignment quality");
		addBoolean("twodonly", false,  "Use only two dimentional reads");				
		addInt("read", 50,  "Minimum number of reads between analyses");		
		addInt("time", 1800,   "Minimum number of seconds between analyses");
		addBoolean("log", false, "Whether to write mapping details to genes2reads.map.");

		addInt("thread", 1,   "Number of threads to run");

		addString("mm2Preset", null,  "mm2Preset ",false);
		addString("mm2_path", "/sw/minimap2/current/minimap2",  "minimap2 path", false);
		addString("readList", null,  "file with reads to include", false);
		addInt("maxReads",Integer.MAX_VALUE, "max reads to process", false );
		long mem = (Runtime.getRuntime().maxMemory()-1000000000);
		addString("mm2_memory", mem+"",  "minimap2 memory", false);
		addDouble("fail_thresh", 7.0,  "median phred quality of read", false);
		addInt("mm2_threads", 4, "threads for mm2", false);
		addDouble("qual", 1,  "Minimum alignment quality");
		
		
		addStdHelp();
	} 
	
	static double scoreThreshold = 0.0010;
	static int readPeriod = 50;
	static int time = 100;
	static int thread = 1;
	static boolean twodonly = false;
   static  int maxReads = Integer.MAX_VALUE;
   static String msa = "kalign";
   static double q_thresh=7;
   static String tmp="_tmpt";
	static File resdir = new File("japsa_resistance_typing");

	public static void main(String[] args) throws IOException, InterruptedException{
		CommandLine cmdLine = new RealtimeResistanceGeneCmd();
		args = cmdLine.stdParseLine(args);		
		scoreThreshold = cmdLine.getDoubleVal("score");		
		readPeriod = cmdLine.getIntVal("read");
		time = cmdLine.getIntVal("time");
		thread = cmdLine.getIntVal("thread");
		twodonly = cmdLine.getBooleanVal("twodonly");
		msa = cmdLine.getStringVal("msa");
		maxReads = cmdLine.getIntVal("maxReads");
		q_thresh = cmdLine.getDoubleVal("fail_thresh");
		resdir = new File(cmdLine.getStringVal("resdir"));
		resdir.mkdirs();
		SequenceUtils.mm2_threads= cmdLine.getIntVal("mm2_threads");
		SequenceUtils.mm2_mem = cmdLine.getStringVal("mm2_mem");
		SequenceUtils.mm2_path = cmdLine.getStringVal("mm2_path");
		SequenceUtils.mm2Preset = cmdLine.getStringVal("mm2Preset");
		SequenceUtils.mm2_splicing = null;//
		SequenceUtils.secondary = true;
		CachedOutput.MIN_READ_COUNT=cmdLine.getIntVal("minCountResistance"); // this for detection of abx
		
		RealtimeSpeciesTyping.MIN_READS_COUNT = cmdLine.getIntVal("minCountSpecies");
		RealtimeResistanceGene.OUTSEQ = cmdLine.getBooleanVal("log");
		RealtimeResistanceGene.runKAlign = cmdLine.getBooleanVal("runKAlign");
		tmp = cmdLine.getStringVal("tmp");		

		
		String output = cmdLine.getStringVal("output");
		String bamFile = cmdLine.getStringVal("bam");		
		File resDB = new File(cmdLine.getStringVal("resDB"));
		String fastqFile = cmdLine.getStringVal("fastqFile");
		String readList = cmdLine.getStringVal("readList");
		
		if(bamFile==null && fastqFile==null) throw new RuntimeException("must define fastqFile or bam file");

		File outdir = new File("./");
		List<String> outfiles = new ArrayList<String>();
		resistanceTyping(resDB,resdir,  bamFile==null ? null : bamFile.split(":"),
				fastqFile==null ? null : fastqFile.split(":"), readList, outdir, output, outfiles);
		//now do species typing on the resistance genes;
		String dbPath =  cmdLine.getStringVal("dbPath");
		String dbs = cmdLine.getStringVal("dbs");//.split(":");
		if(dbPath!=null && dbs!=null && outfiles.size()>0){
			CachedOutput.MIN_READ_COUNT=RealtimeSpeciesTyping.MIN_READS_COUNT;
			RealtimeSpeciesTyping.writeSep = Pattern.compile("[a-z]");
			SequenceUtils.secondary = false;
			ReferenceDB refDB = new ReferenceDB(dbPath, dbs, null);
			List<String> species_output_files = new ArrayList<String>();
			RealtimeSpeciesTypingCmd.speciesTyping(refDB, null, null, null,outfiles.toArray(new String[0]),  "output.dat", species_output_files);
		}
	}

	public static void resistanceTyping(File resDB, File resdir, String[] bamFile, 
			String[] fastqFile,String readList, File outdir, String output, List<String> outfiles)  throws IOException, InterruptedException{
	

		List<File> sample_names = new ArrayList<File>();	
		List<Iterator<SAMRecord>> iterators =  new ArrayList<Iterator<SAMRecord>>();
		List<SamReader> readers =  new ArrayList<SamReader>();
		
		RealtimeSpeciesTypingCmd.getSamIterators(bamFile==null ? null : bamFile, 
				fastqFile==null ? null : fastqFile, readList, maxReads, q_thresh, sample_names,iterators, readers, 
				new File(resDB,"DB.fasta"));
		
		for(int k=0; k<iterators.size(); k++){
			File outdir1 =new File(resdir, sample_names.get(k)+".resistance");
			
			outdir1.mkdirs();
			RealtimeResistanceGene paTyping = new RealtimeResistanceGene(readPeriod, time, outdir1,outdir1.getAbsolutePath()+"/"+output, resDB.getAbsolutePath(), tmp);		
			paTyping.msa = msa;		
			paTyping.scoreThreshold = scoreThreshold;
			paTyping.twoDOnly = twodonly;
			paTyping.numThead = thread;
			paTyping.typing(iterators.get(k));	
			paTyping.close();
			
			paTyping.getOutfiles(outfiles);
		}
		for(int i=0; i<readers.size(); i++){
			readers.get(i).close();
		}
	
	}
}

/*RST*
-------------------------------------------------------------------------------------------------------
*jsa.np.rtResistGenes*: Antibiotic resistance gene identification in real-time with Nanopore sequencing 
-------------------------------------------------------------------------------------------------------

*jsa.np.rtResistGenes* identifies antibiotic resistance genes from real-time sequencing
with Nanopore MinION. 

<usage> 

~~~~~~~~~~
Setting up
~~~~~~~~~~

Refer to the documentation at https://github.com/mdcao/npAnalysis/ for more 
details.


*RST*/
