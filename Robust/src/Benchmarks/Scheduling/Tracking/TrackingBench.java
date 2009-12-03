/** Bamboo version
 *  Ported form SD_VBS 1.0
 * 
 * @author jzhou
 *
 */

task startup(StartupObject s{initialstate}) {
  //System.printString("task startup\n");
 
  int nump = 32; //60;
  TrackDemo tdmo = new TrackDemo(nump){toaddBP};
  
  int[] input = tdmo.getInput(false);
  int pnum = 32; //60;
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

  int pnum = 16; //30;
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
                          pnum,
                          tdmo.getNumP()){tocalcGF};

  taskexit(tdmo{!topreresize, toresize});
}

task resize(TrackDemo tdmo{toresize}) {
  //System.printString("task resize\n");
  
  tdmo.resize();
  
  taskexit(tdmo{!toresize, tomergeIDX});
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
//System.printString("task calcGoodFeature\n");
  
  lda.calcGoodFeature(imxm, imym);
  // validation
  //lda.printImage();
  lda.reshape();
  // validation
  //lda.printImage();
  
  taskexit(lda{!tocalcGF, tocalcInd},
           imxm{!tocalcGF, finish},
           imym{!tocalcGF, finish});
} 

task calcInd(Lambda lda{tocalcInd}) {
//System.printString("task calcInd\n");
  
  int r = lda.getR();
  float[] data = lda.getImage();
  int rows = lda.getRows();
  int cols = lda.getCols();
  int pnum = lda.getNumP();
  int range = rows / pnum;
  for(int i = 0; i < pnum; i++) {
    IDX idx = new IDX(lda.N_FEA,
                      i,
                      range,                 
                      data,
                      rows,
                      cols,
                      r){toprocess};
  }
                    
  taskexit(lda{!tocalcInd, finish});
}

task processIDX(IDX idx{toprocess}) {
//System.printString("task processIDX\n");
  
  idx.fSortIndices();
  // validation
  //idx.printInd();
  
  taskexit(idx{!toprocess, tomergeIDX});
}

task addIDX(TrackDemo tdmo{tomergeIDX}, 
            IDX idx{tomergeIDX}) {
//System.printString("task addIDX\n");

  boolean isfinished = tdmo.addIDX(idx);
  //validation
  //idx.printInd();tdmo.print3f();

  if(isfinished) {
    //tdmo.print3f();
    taskexit(tdmo{!tomergeIDX, tocalcF},
             idx{!tomergeIDX, finish});
  } else {
    taskexit(idx{!tomergeIDX, finish});
  }
}

task calcFeatures(TrackDemo tdmo{tocalcF}) {
//System.printString("task calcFeatures\n");

  tdmo.calcFeatures();

  taskexit(tdmo{!tocalcF, tostartL});
}

task startTrackingLoop(TrackDemo tdmo{tostartL}) {
//System.printString("task startTrackingLoop\n");

  int pnum1 = 8; //15; // * 2;
  float[] data = tdmo.getImage();
  int rows = tdmo.getRows();
  int cols = tdmo.getCols();
  int range = rows / pnum1;
  
  for(int i = 0; i < pnum1; i++) {
    IXL ixl = new IXL(i,
                      range,
                      data,
                      rows,
                      cols){toprocess};
    IYL iyl = new IYL(i,
                      range,
                      data,
                      rows,
                      cols){toprocess};
  }
  IXLM ixlm1 = new IXLM(pnum1,
                        data,
                        rows,
                        cols){tomergeIXL};
  IYLM iylm1 = new IYLM(pnum1,
                        data,
                        rows,
                        cols){tomergeIYL};
           
  data = tdmo.getImageR();
  rows = tdmo.getRowsR();
  cols = tdmo.getColsR(); 
  range = rows / pnum1;
  for(int i = 0; i < pnum1; i++) {
    IXLR ixl = new IXLR(i,
                        range,
                        data,
                        rows,
                        cols){toprocess};
    IYLR imy = new IYLR(i,
                        range,
                        data,
                        rows,
                        cols){toprocess};
  }
  IXLMR ixlm2 = new IXLMR(pnum1,
                          data,
                          rows,
                          cols){tomergeIXLR};
  IYLMR iylm2 = new IYLMR(pnum1,
                          data,
                          rows,
                          cols){tomergeIYLR};
                                 
  int pnum2 = 32; //60; // * 2;
  int[] input = tdmo.getInput(true);
  range = (input[0]) / pnum2;
  for(int i = 0; i < pnum2; i++) {
    BlurPieceL bpl = new BlurPieceL(i,
                                    range,
                                    input){toblur};
  }
  tdmo.setBPLNum(pnum2);  
  tdmo.startTrackingLoop();
  
  taskexit(tdmo{!tostartL, toaddBP2});
}

task blurL(BlurPieceL bpl{toblur}) {
  //System.printString("task blurL\n");
  
  //bpl.printImage();
  bpl.blur();
  
  taskexit(bpl{!toblur, toaddBP});
}

task addBPL(TrackDemo tdmo{toaddBP2},
            BlurPieceL bpl{toaddBP}) {
//System.printString("task addBPL\n");
  
  boolean isfinished = tdmo.addBPL(bpl);

  if(isfinished) {
    tdmo.postBlur();
    taskexit(tdmo{!toaddBP2, toresize2},
             bpl{!toaddBP, finish});
  } else {
    taskexit(bpl{!toaddBP, finish});
  }
}

task resizeL(TrackDemo tdmo{toresize2}) {
//System.printString("task resizeL\n");
  
  tdmo.resize();
  
  taskexit(tdmo{!toresize2, tocalcT});
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

task mergeIXL(IXLM ixlm{tomergeIXL}, 
              IXL ixl{tomergeIXL}) {
//System.printString("task mergeIXL\n");

  boolean isfinished = ixlm.addCalcSobelResult(ixl);

  if(isfinished) {
    ixlm.calcSobel_dX();
    taskexit(ixlm{!tomergeIXL, tocalcT}, 
             ixl{!tomergeIXL, finish});
  } else {
    taskexit(ixl{!tomergeIXL, finish});
  }
}

task mergeIYL(IYLM iylm{tomergeIYL}, 
              IYL iyl{tomergeIYL}) {
//System.printString("task mergeIYL\n");

  boolean isfinished = iylm.addCalcSobelResult(iyl);

  if(isfinished) {
    iylm.calcSobel_dY();
    taskexit(iylm{!tomergeIYL, tocalcT}, 
             iyl{!tomergeIYL, finish});
  } else {
    taskexit(iyl{!tomergeIYL, finish});
  }
}

task processIXLR(IXLR ixl{toprocess}) {
//System.printString("task processIXLR\n");
  
  ixl.calcSobel_dX();
  
  taskexit(ixl{!toprocess, tomergeIXLR});
}

task processIYLR(IYLR iyl{toprocess}) {
//System.printString("task processIYLR\n");
  
  iyl.calcSobel_dY();
  
  taskexit(iyl{!toprocess, tomergeIYLR});
}

task mergeIXLR(IXLMR ixlm{tomergeIXLR}, 
               IXLR ixl{tomergeIXLR}) {
//System.printString("task mergeIXLR\n");

  boolean isfinished = ixlm.addCalcSobelResult(ixl);

  if(isfinished) {
    ixlm.calcSobel_dX();
    taskexit(ixlm{!tomergeIXLR, tocalcT}, 
             ixl{!tomergeIXLR, finish});
  } else {
    taskexit(ixl{!tomergeIXLR, finish});
  }
}

task mergeIYLR(IYLMR iylm{tomergeIYLR}, 
               IYLR iyl{tomergeIYLR}) {
//System.printString("task mergeIYLR\n");

  boolean isfinished = iylm.addCalcSobelResult(iyl);

  if(isfinished) {
    iylm.calcSobel_dY();
    taskexit(iylm{!tomergeIYLR, tocalcT}, 
             iyl{!tomergeIYLR, finish});
  } else {
    taskexit(iyl{!tomergeIYLR, finish});
  }
}

task calcTrack(TrackDemo tdmo{tocalcT},
               IXLM ixlm{tocalcT},
               IYLM iylm{tocalcT},
               IXLMR ixlmr{tocalcT},
               IYLMR iylmr{tocalcT}) {
//System.printString("task calcTrack()\n");

  tdmo.calcTrack(ixlm, iylm, ixlmr, iylmr);

  if(tdmo.isFinish()) {
    //tdmo.printFeatures();
    // finished
    taskexit(tdmo{!tocalcT, finish},
             ixlm{!tocalcT, finish},
             iylm{!tocalcT, finish},
             ixlmr{!tocalcT, finish},
             iylmr{!tocalcT, finish});
  } else {
    taskexit(tdmo{!tocalcT, tostartL},
             ixlm{!tocalcT, finish},
             iylm{!tocalcT, finish},
             ixlmr{!tocalcT, finish},
             iylmr{!tocalcT, finish});
  }
}