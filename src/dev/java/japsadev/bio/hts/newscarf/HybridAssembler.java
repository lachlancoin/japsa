package japsadev.bio.hts.newscarf;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import japsa.util.Logging;

public class HybridAssembler {
	BidirectedGraph graph;
	
	public HybridAssembler(){
		graph=new BidirectedGraph();
	}
	
	public HybridAssembler(BidirectedGraph graph){
		this.graph=graph;
	}
	
	public HybridAssembler(String graphFile) throws IOException{
		graph=new BidirectedGraph();
		graph.loadFromFile(graphFile);
	}
	
	public HybridAssembler(String graphFile, String pathFile) throws IOException{
		this(graphFile);
		graph.readPathsFromSpades(pathFile);
	}
	
	
	public void assembly(String bamFile, int qual) throws IOException{
		SamReaderFactory.setDefaultValidationStringency(ValidationStringency.SILENT);

		SamReader reader;
		if ("-".equals(bamFile))
			reader = SamReaderFactory.makeDefault().open(SamInputResource.of(System.in));
		else
			reader = SamReaderFactory.makeDefault().open(new File(bamFile));	

		SAMRecordIterator iter = reader.iterator();

		String readID = "";
		//ReadFilling readFilling = null;
		ArrayList<Alignment> samList =  new ArrayList<Alignment>();;// alignment record of the same read;	
		BidirectedPath p = new BidirectedPath();
		while (iter.hasNext()) {
			SAMRecord rec = iter.next();
			if (rec.getReadUnmappedFlag())
				continue;
			if (rec.getMappingQuality() < qual)
				continue;

			Alignment myRec = new Alignment(rec, graph.getNode(rec.getReferenceIndex()));


			//////////////////////////////////////////////////////////////////
			// make list of alignments of the same (Nanopore) read. 
			// Note that SAM file MUST be sorted based on readID (samtools sort -n)

			//not the first occurrance				
			if (!readID.equals("") && readID.equals(myRec.readID)) {		
				Collections.sort(samList);
				p=graph.pathFinding(samList);
				graph.reduce(p);
				samList = new ArrayList<Alignment>();
				readID = myRec.readID;	
			}			
			samList.add(myRec);

		}// while
		iter.close();

		//outOS.close();
		reader.close();		

		Logging.info("Sort list of bridges");		
		//Collections.sort(bridgeList);		
	}

	public static void main(String[] argv) throws IOException{
		HybridAssembler hbAss = new HybridAssembler("/home/s.hoangnguyen/Projects/scaffolding/data/spades_3.7/EcK12S-careful/assembly_graph.fastg");
		//run bwa first on the edited assembly_graph.fastg by running:
		//awk -F '[:;]' -v q=\' 'BEGIN{flag=0;}/^>/{if(index($1,q)!=0) flag=0; else flag=1;}{if(flag==1) print $1;}' ../EcK12S-careful/assembly_graph.fastg > Eck12-careful.fasta
		hbAss.assembly("/home/s.hoangnguyen/Projects/scaffolding/data/spades_3.7/EcK12S-careful.sam", 0);
	}
	
}