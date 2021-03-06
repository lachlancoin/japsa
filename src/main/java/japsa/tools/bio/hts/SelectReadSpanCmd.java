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

/****************************************************************************
 *                           Revision History                                
 * 08/03/2013 - Minh Duc Cao: Started
 * 24/06/2013 - Minh Duc Cao: updated                   
 *  
 ****************************************************************************/

package japsa.tools.bio.hts;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SAMTextWriter;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.SAMFileHeader.SortOrder;
import japsa.bio.tr.TandemRepeat;
import japsa.seq.SequenceReader;
import japsa.util.CommandLine;
import japsa.util.deploy.Deployable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


/**
 * Select reads that span an STR from a bam/sam file
 * FIXME: 1. Generalise to any features, not just STR
 * FIXME: 2. Test outputing to sam/bam file
 */

@Deployable(
	scriptName = "jsa.hts.selectSpan",
	scriptDesc = "Filter reads that span some regions from a sorted b/sam file"
	)
public class SelectReadSpanCmd extends CommandLine{	
	public SelectReadSpanCmd(){
		super();
		Deployable annotation = getClass().getAnnotation(Deployable.class);		
		setUsage(annotation.scriptName() + " [options]");
		setDesc(annotation.scriptDesc());

		addStdInputFile();		
		addString("trFile", null, "Name of the tr file",true);		
		addString("output", "-", "Name of output sam file, - for from standard out.");

		addStdHelp();		
	} 	
	static int pad = 3;

	public static void main(String[] args) throws Exception {
		CommandLine cmdLine = new SelectReadSpanCmd();
		args = cmdLine.stdParseLine(args);

		String output = cmdLine.getStringVal("output");
		String samFile = cmdLine.getStringVal("input");
		String trFile = cmdLine.getStringVal("trFile");

		filterSam(samFile, output, trFile);		
	}

	static void filterSam(String inFile, String outFile, String trFile)
		throws IOException {				
		/////////////////////////////////////////////////////////////////////////////
		SamReaderFactory.setDefaultValidationStringency(ValidationStringency.SILENT);
		SamReader samReader = SamReaderFactory.makeDefault().open(new File(inFile));						


		SAMFileHeader samHeader = samReader.getFileHeader();		
		SAMTextWriter samWriter = new SAMTextWriter(new File(outFile));
		samWriter.setSortOrder(SortOrder.unsorted, false);		
		samWriter.writeHeader( samHeader.getTextHeader());

		ArrayList<TandemRepeat>  myList = TandemRepeat.readFromFile(SequenceReader.openFile(trFile), new ArrayList<String>());
		TandemRepeat tr = myList.get(0);

		System.err.print(tr.toString()+" : ");
		int trIndex = 0;
		int count = 0;

		int trSeqIndex = samHeader.getSequenceIndex(tr.getChr());
		if (trSeqIndex < 0){
			samReader.close();
			samWriter.close();
			throw new RuntimeException("Sequence " + tr.getChr() + " not found in the header of b/sam file " + inFile);
		}

		SAMRecordIterator samIter = samReader.iterator();
		while (samIter.hasNext()){
			SAMRecord sam = samIter.next();			


			int seqIndex = sam.getReferenceIndex();

			//the samrecod is in an ealier sequence
			if (seqIndex < trSeqIndex)
				continue;

			int posStart = sam.getAlignmentStart();
			int posEnd = sam.getAlignmentEnd();

			if (seqIndex == trSeqIndex && posEnd <= tr.getEnd() + pad)
				continue;

			if (seqIndex == trSeqIndex && posStart < tr.getStart() - pad){
				samWriter.addAlignment(sam);
				count ++;
				continue;
			}

			while (seqIndex > trSeqIndex || (seqIndex == trSeqIndex && posStart > tr.getStart() - pad)){
				trIndex ++;
				System.err.println(count);
				count = 0;
				if (trIndex < myList.size()){
					tr = myList.get(trIndex);
					trSeqIndex = samHeader.getSequenceIndex(tr.getChr());
					if (trSeqIndex < 0){
						samReader.close();
						samWriter.close();
						throw new RuntimeException("Sequence " + tr.getChr() + " not found in the header of b/sam file " + inFile);
					}
					System.err.print(tr.toString()+" : ");					
				}else{
					tr = null;
					break;//while
				}
			}

			if (tr == null)
				break;

			if (seqIndex == trSeqIndex && posStart > tr.getStart() - pad && posEnd < tr.getEnd() + pad){
				samWriter.addAlignment(sam);
				count ++;
				continue;
			}
		}
		samWriter.close();
		samReader.close();
	}
}


