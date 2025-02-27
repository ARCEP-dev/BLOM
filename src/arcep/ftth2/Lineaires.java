/*
 * Copyright (c) 2025, Autorité de régulation des communications électroniques, des postes et de la distribution de la presse
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package arcep.ftth2;

public class Lineaires {
    
    private final double[] cablesFA;
    private final double[] cablesFS;
    
    private final int[] boitiersA;
    private final int[] boitiersS;
    
    private long nbEpissuresA;
    private long nbEpissuresS;
    
    private final double[] longueurGC;
    private final double[] volumeGC;
    
    private final int[] incomingCables;
    private int nbFibres;

    // constructeur de linéaire "vide"
    public Lineaires(Parametres parametres){
        int maxAerien = parametres.getCalibreMax(0);
        int maxSouterrain = parametres.getCalibreMax(7);
        
        cablesFA = new double[maxAerien+1];
        for (int i = 0;i<=maxAerien;i++){
            cablesFA[i] = 0.0;
        }
        cablesFS = new double[maxSouterrain+1];
        for (int i = 0;i<=maxSouterrain;i++){
            cablesFS[i] = 0.0;
        }
        
        boitiersA = new int[maxAerien+1];
        boitiersS = new int[maxSouterrain+1];

        nbEpissuresA = 0;
        nbEpissuresS = 0;
        
        longueurGC = new double[]{0,0,0,0};
        volumeGC = new double[]{0,0,0,0};
        
        incomingCables = new int[maxSouterrain+1-parametres.getCalibreMin()]; // que pour incomingCables
        nbFibres = 0;
    }
    
    // constructeur de linéaire utilisé par AreteBLOM pour produire le sien
    public Lineaires(Parametres parametres, int modePoseSortie, double longueur, int[] nbCables, int[] boitiers, int nbEpissures, double section, double sectionTotale){
        int maxAerien = parametres.getCalibreMax(0);
        int maxSouterrain = parametres.getCalibreMax(7);
        double[] cables = this.produitScalaire(nbCables, longueur*parametres.longFibreSupp);
        
        switch(modePoseSortie){
            case 0:
            case 1:
                this.cablesFA =  cables;
                this.boitiersA = boitiers;
                this.nbEpissuresA = nbEpissures;
                this.cablesFS = new double[maxSouterrain+1];
                for (int i = 0;i<=maxSouterrain;i++){
                    this.cablesFS[i] = 0.0;
                }
                this.boitiersS = new int[maxSouterrain+1];
                this.nbEpissuresS = 0;
                break;
            default:
                this.cablesFS = cables;
                this.boitiersS = boitiers;
                this.nbEpissuresS = nbEpissures;
                this.cablesFA = new double[maxAerien+1];
                for (int i = 0;i<=maxAerien;i++){
                    this.cablesFA[i] = 0.0;
                }
                this.boitiersA = new int[maxAerien+1];
                this.nbEpissuresA = 0;
                break;
        }
        
        longueurGC = new double[]{0,0,0,0};
        volumeGC = new double[]{0,0,0,0};
        volumeGC[modePoseSortie]+= section*longueur;
        longueurGC[modePoseSortie]+= longueur*section/sectionTotale; // bonne formule à utiliser pour ne pas doublonner les longueurs de GC
        //if (a.getSection(isDistri, zone) > 0) longueurGC[modePoseSortie]+= longueur;  // formule de test à commenter en fonctionnement réel
        
        incomingCables = new int[maxSouterrain+1-parametres.getCalibreMin()]; // que pour incomingCables
        nbFibres = 0;
    }
    
    public void addIncomingCables(int[] cables, int[] calibre, int calibreMin){
        for (int i = calibreMin;i<cables.length;i++){
            incomingCables[i-calibreMin]+=cables[i];
            nbFibres+=cables[i]*calibre[i];
        }
    }

    public void addLineaires(Lineaires lin){
        addArray(this.cablesFA, lin.cablesFA);
        addArray(this.cablesFS, lin.cablesFS);
        addArray(this.boitiersA, lin.boitiersA);
        addArray(this.boitiersS, lin.boitiersS);
        addArray(this.longueurGC, lin.longueurGC);
        addArray(this.volumeGC, lin.volumeGC);
        addArray(this.incomingCables, lin.incomingCables);
        this.nbFibres+=lin.nbFibres;
        this.nbEpissuresA += lin.nbEpissuresA;
        this.nbEpissuresS += lin.nbEpissuresS;
    }

    public int getNbFibres(){
        return this.nbFibres;
    }

    public static String header(Parametres parametres, String prefixe, int detail){
        String header;
        switch(detail){
            case 1:
                header = prefixe+"longueur GC existant aerien";
                header += prefixe+"longueur GC PT reconstruit aerien";
                header += prefixe+"longueur GC existant souterrain";
                header += prefixe+"longueur GC PT reconstruit conduite";
                break;
            case 2:
                header = prefixe+"longueur GC existant aerien";
                header += prefixe+"longueur GC PT reconstruit aerien";
                header += prefixe+"longueur GC existant souterrain";
                header += prefixe+"longueur GC PT reconstruit conduite";
                header += prefixe+"volume GC existant aerien";
                header += prefixe+"volume GC PT reconstruit aerien";
                header += prefixe+"volume GC existant souterrain";
                header += prefixe+"volume GC PT reconstruit conduite";
                break;
            default:
                header =prefixe+"longueur GC";
                header+=prefixe+"volume GC";
        }
        int maxAerien = parametres.getCalibreMax(0);
        int maxSouterrain = parametres.getCalibreMax(7);
        int minHorizontal = parametres.getCalibreMin();
        for (int i = minHorizontal;i<=maxAerien;i++){
            header+= prefixe+"F_A_"+parametres.calibre[i];
        }
        for (int i = minHorizontal;i<=maxSouterrain;i++){
            header+= prefixe+"F_S_"+parametres.calibre[i];
        }
        for (int i = minHorizontal;i<=maxAerien;i++){
            header+= prefixe+"B_A_"+parametres.calibre[i];
        }
        for (int i = minHorizontal;i<=maxSouterrain;i++){
            header+= prefixe+"B_S_"+parametres.calibre[i];
        }
        header +=prefixe+"Epissures aeriennes"+prefixe+"Epissures souterraines";
        return header;
    }

    public String print(Parametres parametres, int detail){
        String suite = "";
        switch(detail){
            case 1:
                for (double d : longueurGC){
                    suite+=";"+String.valueOf(d).replace(".", ",");
                }
                break;
            case 2:
                for (double d : longueurGC){
                    suite+=";"+String.valueOf(d).replace(".", ",");
                }
                for (double d : volumeGC){
                    suite+=";"+String.valueOf(d).replace(".", ",");
                }
                break;
            default:
                double longueurTotale = 0, volumeTotal = 0;
                for (int i = 0;i<longueurGC.length;i++){
                    longueurTotale+=longueurGC[i];
                    volumeTotal += volumeGC[i];
                }
                suite += ";"+String.valueOf(longueurTotale).replace(".", ",")+";"+
                        String.valueOf(volumeTotal).replace(".", ",");
        }
        
        int maxAerien = parametres.getCalibreMax(0);
        int maxSouterrain = parametres.getCalibreMax(7);
        int minHorizontal = parametres.getCalibreMin();
        for (int i = minHorizontal;i<=maxAerien;i++){
            suite+=";"+String.valueOf(cablesFA[i]).replace(".", ",");
        }
        for (int i = minHorizontal;i<=maxSouterrain;i++){
            suite+=";"+String.valueOf(cablesFS[i]).replace(".", ",");
        }
        for (int i = minHorizontal;i<=maxAerien;i++){
            suite+=";"+boitiersA[i];
        }
        for (int i = minHorizontal;i<=maxSouterrain;i++){
            suite+=";"+boitiersS[i];
        }
        suite+=";"+this.nbEpissuresA+";"+this.nbEpissuresS;
        return suite;
    }
    
    public String printIncomingCables(){
        String debut = "";
        for (int i = 0;i<incomingCables.length;i++){
                debut += ";"+incomingCables[i];
            }
        return debut + ";" + nbFibres;
    }
    
    public double[] coutsLineaires(CoutsUnitaires couts, Parametres parametres){
        double[] res = new double[7];
        res[0] = product(cablesFA,couts.CoutUnitaireCablesAeriens);
        res[1] = product(cablesFS,couts.CoutUnitaireCablesSouterrains);
        res[2] = product(boitiersA,couts.CoutUnitaireBoitiersAeriens);
        res[3] = product(boitiersS,couts.CoutUnitaireBoitiersSouterrains);
        res[4] = longueurGC[1]*couts.CoutUnitairePoteau;
        res[5] = longueurGC [3]*couts.CoutUnitaireConduiteAllegee;
        res[6] = longueurGC[0]*couts.etudesAerien+longueurGC[2]*couts.etudesSouterrain;
        return res;
    }
    
    private void addArray(double[] t1, double[] t2){
        int n = t1.length;
        if (n==t2.length){
            for (int i = 0;i<n;i++){
                t1[i]+=t2[i];
            }
        } else
            System.out.println("Les tableaux n'ont pas la même longueur !");
    }
    
    private void addArray(int[] t1, int[] t2){
        int n = t1.length;
        if (n==t2.length){
            for (int i = 0;i<n;i++){
                t1[i]+=t2[i];
            }
        } else
            System.out.println("Les tableaux n'ont pas la même longueur !");
    }
    
    private double product(double[] uo, double[] couts){
        double res = 0;
        for (int i = 0; i < uo.length;i++){
            res+= uo[i]*couts[i];
        }
        return res;
    }
    
    private double product(int[] uo, double[] couts){
        double res = 0;
        for (int i = 0; i < uo.length;i++){
            res+= uo[i]*couts[i];
        }
        return res;
    }
    
    private double[] produitScalaire(int[] cables, double longueur){
        double[] res = new double[cables.length];
        for (int i = 0;i<cables.length;i++){
            res[i] = longueur*cables[i];
        }
        return res;
    }
}
