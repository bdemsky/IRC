/** Bamboo version
 *  Ported form SD_VBS 1.0
 * 
 * @author jzhou
 *
 */

task startup(StartupObject s{initialstate}) {
  //System.printString("task startup\n");
 
  TrackDemo tdmo = new TrackDemo(){toaddBP};
  
  int[] input = tdmo.getInput(false);
  int pnum = 4;
  int range = (input[0]) / pnum;
  for(int i = 0; i < pnum; i++) {
    BlurPiece bp = new BlurPiece(i,
                                 range,
                                 input){toblur};
  }
  tdmo.setBPNum(pnum);
  
  taskexit(s{!initialstate});
}

task blur(BlurPiece bp{toblur}) {
  //System.printString("task blur\n");
  
  //bp.printImage();
  bp.blur();
  
  taskexit(bp{!toblur, toaddBP});
}

task addBP(TrackDemo tdmo{toaddBP},
           BlurPiece bp{toaddBP}) {
  //System.printString("task addBP\n");
  
  boolean isfinished = tdmo.addBP(bp);
  
  if(isfinished) {
    tdmo.postBlur();
    //tdmo.printImage();
    taskexit(tdmo{!toaddBP, topreresize},
             bp{!toaddBP, finish});
  } else {
    taskexit(bp{!toaddBP, finish});
  }
}

task preresize(TrackDemo tdmo{topreresize}) {
//System.printString("task preresize\n");

  float[] Icur = tdmo.getImage();

  int pnum = 4;
  int range = (tdmo.getRows()) / pnum;
  int rows = tdmo.getRows();
  int cols = tdmo.getCols();
//create ImageX to calc Sobel_dX
  for(int i = 0; i < pnum; i++) {
    ImageX imageX = new ImageX(i,
                               range,
                               Icur, 
                               rows, 
                               cols){toprocess};
  }
  ImageXM imageXM = new ImageXM(pnum,
                                rows,
                                cols){tomergeX};
// create ImageY to calc Sobel_dY
  for(int i = 0; i < pnum; i++) {
    ImageY imageY = new ImageY(i,
                               range,
                               Icur, 
                               rows, 
                               cols){toprocess};
  }
  ImageYM imageYM = new ImageYM(pnum,
                                rows,
                                cols){tomergeY};
//create a Lambda to aggregate results from the ImageXs
  Lambda lda = new Lambda(tdmo.WINSZ,
                          tdmo.N_FEA,
                          pnum){tocalcGF};

  taskexit(tdmo{!topreresize, toresize});
}

task resize(TrackDemo tdmo{toresize}) {
  //System.printString("task resize\n");
  
  tdmo.resize();
  
  taskexit(tdmo{!toresize, toaddLMDA});
}

task processImageX(ImageX imx{toprocess}) {
//System.printString("task processImageX\n");
  
  imx.calcSobel_dX();
  //imx.printResult();
  
  taskexit(imx{!toprocess, tomergeX});
}

task processImageY(ImageY imy{toprocess}) {
//System.printString("task processImageY\n");
  
  imy.calcSobel_dY();
  //imy.printResult();
  
  taskexit(imy{!toprocess, tomergeY});
}

task mergeX(ImageXM imxm{tomergeX}, 
            ImageX imx{tomergeX}) {
//System.printString("task mergeX\n");

  boolean isfinished = imxm.addCalcSobelResult(imx);

  if(isfinished) {
    imxm.calcSobel_dX();
    taskexit(imxm{!tomergeX, tocalcGF}, 
             imx{!tomergeX, finish});
  } else {
    taskexit(imx{!tomergeX, finish});
  }
}

task mergeY(ImageYM imym{tomergeY}, 
            ImageY imy{tomergeY}) {
//System.printString("task mergeY\n");

  boolean isfinished = imym.addCalcSobelResult(imy);

  if(isfinished) {
    imym.calcSobel_dY();
    taskexit(imym{!tomergeY, tocalcGF}, 
             imy{!tomergeY, finish});
  } else {
    taskexit(imy{!tomergeY, finish});
  }
}

task calcGoodFeature(Lambda lda{tocalcGF},
                     ImageXM imxm{tocalcGF},
                     ImageYM imym{tocalcGF}) {
//System.printString("task reshape\n");
  
  lda.calcGoodFeature(imxm, imym);
  // validation
  //lda.printImage();
  int r = lda.reshape();
  // validation
  //lda.printImage();
  lda.sortInd(r);
  // validation
  //lda.printResult();
  lda.fSortIndices();
  // validation
  //lda.printResult();
  
  taskexit(lda{!tocalcGF, toaddLMDA},
           imxm{!tocalcGF, finish},
           imym{!tocalcGF, finish});
}

task addLMDA(TrackDemo tdmo{toaddLMDA}, 
             Lambda lmda{toaddLMDA}) {
//System.printString("task addLMDA\n");

  tdmo.addLMDA(lmda);

  taskexit(tdmo{!toaddLMDA, tocalcF},
           lmda{!toaddLMDA, finish});
}

task calcFeatures(TrackDemo tdmo{tocalcF}) {
  //System.printString("task calcFeatures\n");
  
  tdmo.calcFeatures();
  
  taskexit(tdmo{!tocalcF, tostartL});
}

task startTrackingLoop(TrackDemo tdmo{tostartL}) {
//System.printString("task startTrackingLoop\n");

  int pnum1 = 4;
  float[] data = tdmo.getImage();
  int rows = tdmo.getRows();
  int cols = tdmo.getCols();
  int range = rows / pnum1;
  
  tag t1=new tag(link);
  for(int i = 0; i < pnum1; i++) {
    IXL ixl = new IXL(i,
                      range,
                      0, 
                      data,
                      rows,
                      cols){toprocess}{t1};
    IYL iyl = new IYL(i,
                      range,
                      0,
                      data,
                      rows,
                      cols){toprocess}{t1};
  }
  IXLM ixlm1 = new IXLM(0,
                        pnum1,
                        data,
                        rows,
                        cols){tomergeIXL}{t1};
  IYLM iylm1 = new IYLM(0,
                        pnum1,
                        data,
                        rows,
                        cols){tomergeIYL}{t1};
           
  data = tdmo.getImageR();
  rows = tdmo.getRowsR();
  cols = tdmo.getColsR(); 
  range = rows / pnum1;
  tag t2=new tag(link);
  for(int i = 0; i < pnum1; i++) {
    IXL ixl = new IXL(i,
                      range,
                      1, 
                      data,
                      rows,
                      cols){toprocess}{t2};
    IYL imy = new IYL(i,
                      range,
                      1,
                      data,
                      rows,
                      cols){toprocess}{t2};
  }
  IXLM ixlm2 = new IXLM(1,
                        pnum1,
                        data,
                        rows,
                        cols){tomergeIXL}{t2};
  IYLM iylm2 = new IYLM(1,
                        pnum1,
                        data,
                        rows,
                        cols){tomergeIYL}{t2};
                                 
  int pnum2 = 4;
  int[] input = tdmo.getInput(true);
  range = (input[0]) / pnum2;
  for(int i = 0; i < pnum2; i++) {
    BlurPiece bp = new BlurPiece(i,
                                 range,
                                 input){toblur};
  }
  tdmo.setBPNum(pnum2);                      
  tdmo.startTrackingLoop();
  
  taskexit(tdmo{!tostartL, toaddBP2});
}

task addBPL(TrackDemo tdmo{toaddBP2},
            BlurPiece bp{toaddBP}) {
//System.printString("task addBPL\n");
  
  boolean isfinished = tdmo.addBP(bp);

  if(isfinished) {
    tdmo.postBlur();
    taskexit(tdmo{!toaddBP2, toresize2},
             bp{!toaddBP, finish});
  } else {
    taskexit(bp{!toaddBP, finish});
  }
}

task resizeL(TrackDemo tdmo{toresize2}) {
//System.printString("task resizeL\n");
  
  tdmo.resize();
  
  taskexit(tdmo{!toresize2, toaddIXL, toaddIYL});
}

task processIXL(IXL ixl{toprocess}) {
//System.printString("task processIXL\n");
  
  ixl.calcSobel_dX();
  
  taskexit(ixl{!toprocess, tomergeIXL});
}

task processIYL(IYL iyl{toprocess}) {
//System.printString("task processIYL\n");
  
  iyl.calcSobel_dY();
  
  taskexit(iyl{!toprocess, tomergeIYL});
}

task mergeIXL(IXLM ixlm{tomergeIXL}{link t}, 
              IXL ixl{tomergeIXL}{link t}) {
//System.printString("task mergeIXL\n");

  boolean isfinished = ixlm.addCalcSobelResult(ixl);

  if(isfinished) {
    ixlm.calcSobel_dX();
    taskexit(ixlm{!tomergeIXL, toaddIXL}, 
             ixl{!tomergeIXL, finish});
  } else {
    taskexit(ixl{!tomergeIXL, finish});
  }
}

task mergeIYL(IYLM iylm{tomergeIYL}{link t}, 
              IYL iyl{tomergeIYL}{link t}) {
//System.printString("task mergeIYL\n");

  boolean isfinished = iylm.addCalcSobelResult(iyl);

  if(isfinished) {
    iylm.calcSobel_dY();
    taskexit(iylm{!tomergeIYL, toaddIYL}, 
             iyl{!tomergeIYL, finish});
  } else {
    taskexit(iyl{!tomergeIYL, finish});
  }
}

task addIXLM(TrackDemo tdmo{toaddIXL},
             IXLM ixlm{toaddIXL}) {
//System.printString("task addIXLM()\n");

  if(tdmo.addIXLM(ixlm)) {
//  finished
    taskexit(tdmo{!toaddIXL, tocalcT},
             ixlm{!toaddIXL, finish});
  } else {
    taskexit(ixlm{!toaddIXL, finish});
  }
}

task addIYLM(TrackDemo tdmo{toaddIYL},
             IYLM iylm{toaddIYL}) {
//System.printString("task addIYLM()\n");

  if(tdmo.addIYLM(iylm)) {
//  finished
    taskexit(tdmo{!toaddIYL, tocalcT},
             iylm{!toaddIYL, finish});
  } else {
    taskexit(iylm{!toaddIYL, finish});
  }
}

task calcTrack(TrackDemo tdmo{!toaddIXL && !toaddIYL && tocalcT}) {
//System.printString("task calcTrack()\n");

  tdmo.calcTrack();

  if(tdmo.isFinish()) {
    //tdmo.printFeatures();
    // finished
    taskexit(tdmo{!tocalcT, finish});
  } else {
    taskexit(tdmo{!tocalcT, tostartL});
  }
}