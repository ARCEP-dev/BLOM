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

import java.util.*;

public class UO {

    private final String zone;
    
    private final boolean isDistri;
    private final boolean isTransport;
    
    /// UO si isDistri
    private int nbLignesExt = 0;
    private int nbLignesInt = 0;
    private final Lineaires linPMPBO;
    
    private int nbPMext = 0;
    // nb armoires de PM ext
    private int nbPM_ZMD = 0;
    private int nbPM_ZTD_BD = 0;
    private int nbPM_ZTD_HD = 0;
    
    private final int[] nbPBO;
   
    /// UO si isTransport
    private final Lineaires linNROPM;

    // PM int en ZTD_HD
    private int nbPMint = 0;
    private int nbArmoiresPMint = 0;
    private int nbPBOPMint = 0;
    
    // coupleurs (1:32 au niveau du PM en ZMD et ZTD-BD, 1:8 et 1:4 en ZTD-HD, 2:2 au NRO)
    private final int[] nbCoupleurs; // dans l'ordre 2:2, 1:4, 1:8, 1:16, 1:32, 1:64
    private final int[] nbTiroirsCoupleurs; // dans l'ordre au NRO, au PM int, au PM ext
    
    // au niveau du NRO
    private int nbNRO = 0;
    private int nbTiroirsOptiques = 0;
    private int nbRTO = 0;
    private double surfaceNRO = 0;

    // partie verticale de la distribution calculée à la maille du NRO
    private int nbImmeubles = 0;
    private int nbPBOimmeubles = 0;
    private int nbLogementsImmeubles = 0;

    public UO(String zone, boolean isDistri, boolean isTransport, Parametres parametres){
        this.zone = zone;
        this.isDistri = isDistri;
        if (isDistri & !isTransport) this.nbPMext = 1;
        
        this.isTransport = isTransport;
        linPMPBO = new Lineaires(parametres);
        linNROPM = new Lineaires(parametres);
        nbPBO = new int[4];
        nbCoupleurs = new int[6];
        nbTiroirsCoupleurs = new int[3];
    }

    public void setLignesEtPM(int nbLignes, Parametres parametres){
        // utilisé dans le cas isDistri
        this.nbLignesExt = nbLignes;
        int nbPM = (int) Math.ceil(nbLignes/ (double) parametres.getNbMaxLignesPM(zone));
        switch(zone){
            case "ZTD_HD":
                this.nbPM_ZTD_HD += nbPM;
                break;
            case "ZTD_BD":
                this.nbPM_ZTD_BD += nbPM;
                break;
            default:
                this.nbPM_ZMD += nbPM;
        }
        this.nbCoupleurs[4] = (int) Math.ceil(this.nbLignesExt / (double) parametres.getLignesParCoupleurs("PMext"))*parametres.facteurDuplicationPresenceOC;
        this.nbTiroirsCoupleurs[2] = (int) Math.ceil(this.nbCoupleurs[4] / (double) parametres.getCoupleursParTiroirs("PMext"));
    }

    public int getNbLignes(){
        return this.nbLignesExt+this.nbLignesInt;
    }

    
    public int getNbArmoires() {
        return this.nbArmoiresPMint + this.nbPM_ZMD + this.nbPM_ZTD_BD + this.nbPM_ZTD_HD;
    }
    
    
    public void addLineaires(Lineaires lineaires){
        if (isTransport) this.linNROPM.addLineaires(lineaires);
        else this.linPMPBO.addLineaires(lineaires);
    }

    public void addPBO(int nbPBO, int modePose){
        this.nbPBO[modePose] += nbPBO;
    }

    public void addUO(UO uo){
        this.nbLignesExt += uo.nbLignesExt;
        this.nbLignesInt += uo.nbLignesInt;
        this.linPMPBO.addLineaires(uo.linPMPBO);
        
        for (int i = 0;i<this.nbPBO.length;i++){
            this.nbPBO[i]+=uo.nbPBO[i];
        }
        
        this.nbPMext += uo.nbPMext;
        this.nbPM_ZMD += uo.nbPM_ZMD;
        this.nbPM_ZTD_BD += uo.nbPM_ZTD_BD;
        this.nbPM_ZTD_HD += uo.nbPM_ZTD_HD;
        
        this.linNROPM.addLineaires(uo.linNROPM);
        
        this.nbPMint += uo.nbPMint;
        this.nbPBOPMint += uo.nbPBOPMint;
        this.nbArmoiresPMint += uo.nbArmoiresPMint;

        for (int i = 0; i<this.nbCoupleurs.length;i++){
            this.nbCoupleurs[i]+=uo.nbCoupleurs[i];
        }
        for (int i = 0; i<this.nbTiroirsCoupleurs.length;i++){
            this.nbTiroirsCoupleurs[i]+=uo.nbTiroirsCoupleurs[i];
        }
        
        // au niveau du NRO
        this.nbNRO += uo.nbNRO;
        this.nbTiroirsOptiques += uo.nbTiroirsOptiques;
        this.nbRTO += uo.nbRTO;
        this.surfaceNRO += uo.surfaceNRO;

        // partie verticale de la distribution calculée à la maille du NRO
        this.nbImmeubles += uo.nbImmeubles;
        this.nbPBOimmeubles += uo.nbPBOimmeubles;
        this.nbLogementsImmeubles += uo.nbLogementsImmeubles;
        
    }

    public void addIncomingCables(int[] cables, boolean distri, int[] calibre, int calibreMin){
        if (distri)
            this.linPMPBO.addIncomingCables(cables, calibre, calibreMin);
        else
            this.linNROPM.addIncomingCables(cables, calibre, calibreMin);
    }
    
    public int getNbIncomingFibres(boolean PM){
        if (PM)
            return this.linPMPBO.getNbFibres();
        else
            return this.linNROPM.getNbFibres();
    }

    public void setPMint(List<Noeud> listePMint, Parametres parametres){
        this.nbPMint = listePMint.size();
        for (Noeud pm : listePMint){
            int demandeLocale = pm.demandeLocaleInt();
            this.nbLignesInt += demandeLocale;
            this.nbPBOPMint += (int) Math.ceil(demandeLocale / (double) parametres.nbMaxLignesPBO);
            this.nbArmoiresPMint += (int) Math.ceil(demandeLocale / (double) parametres.getNbMaxLignesPM("ZTD_HD_int"));
            this.nbCoupleurs[2] = (int) Math.ceil(demandeLocale / (double) parametres.getLignesParCoupleurs("PMint"))*parametres.facteurDuplicationPresenceOC;
            this.nbTiroirsCoupleurs[1] = (int) Math.ceil(nbCoupleurs[2] / (double) parametres.getCoupleursParTiroirs("PMint")); 
        }
    }

    public void setVertical(int[] immeublesAbsolu, Parametres parametres){
        int nbLogementsFichierImmeubles = 0;
        for (int i = 0;i<immeublesAbsolu.length;i++){
            nbLogementsFichierImmeubles += i*immeublesAbsolu[i];
        }
        double ratioCorrectif = this.getNbLignes()/(double) nbLogementsFichierImmeubles;
        int[] immeubles = new int[immeublesAbsolu.length];
        for (int i = 0;i<immeubles.length;i++){
            immeubles[i] = (int) Math.round(immeublesAbsolu[i]*ratioCorrectif);
        }
        for (int i = 5;i<9;i++){ // NB : on ne compte à ce stade pas les batiments avec un nombre de locaux trop petits, qui ne constituent pas des immeubles a priori
            nbImmeubles += immeubles[i];
            nbPBOimmeubles += immeubles[i]*Math.ceil(i/ (double) parametres.nbMaxLignesPBO);
        }
        for (int i = 9;i<immeubles.length;i++){ // NB :  on compte les logements supplémentaires au-dessus de 8 
            nbImmeubles += immeubles[i];
            nbPBOimmeubles += immeubles[i]*Math.ceil(i/ (double) parametres.nbMaxLignesPBO);
            nbLogementsImmeubles += immeubles[i]*i;
        }
        int reste = this.nbPBOimmeubles - this.nbPBOPMint;
        int modePose = 3;
        while (reste > 0 && modePose >= 0){
            int n = nbPBO[modePose];
            nbPBO[modePose] = Math.max(n - reste, 0);
            reste -= n;
            modePose--;
        }
        if (reste > 0) System.out.println("Plus de PBOint estimés que le total de PBO trouvés !"); 
    }

    public void setNRO(Parametres parametres){
        this.nbNRO = 1;
        this.nbCoupleurs[0] = (int) Math.ceil(linNROPM.getNbFibres() / (double) parametres.getLignesParCoupleurs("NRO")); 
        this.nbTiroirsCoupleurs[0] = (int) Math.ceil(nbCoupleurs[0] / (double) parametres.getCoupleursParTiroirs("NRO")); 
        this.nbTiroirsOptiques = (int) Math.ceil(linNROPM.getNbFibres() / (double) parametres.nbFibresParTiroir);
        this.nbRTO = (int) Math.ceil((this.nbTiroirsOptiques+this.nbTiroirsCoupleurs[0]) / (double) parametres.nbTiroirsParRTO); // les tiroirs optiques et tirois coupleurs peuvent s'insérer dans les mêmes RTO
        this.surfaceNRO = nbRTO*parametres.surfaceRTO*parametres.facteurSurface;
    }

    public int[] getPBO(){
        if (!this.isDistri)
            System.out.println("Mauvais appel sur un objet UO sans la distri !");
        int[] res = new int[5];
        for (int i = 0;i<4;i++){
            res[i] = this.nbPBO[i];
        }
        res[4] = this.nbPBOimmeubles;
        return res; 
    }

    static public String header(Parametres parametres, boolean distri, boolean transport, int detail){
        String header = "Nb lignes";
        if (transport){
            if (distri){
                if (detail > 0)
                    header += ";Nb de lignes ext;Nb de lignes int";
                header+= ";NRO - nb";
                if (detail > 0)
                    header+= ";NRO - tiroirs optiques;NRO - Coupleurs 2:2;NRO - Tiroirs coupleurs;NRO - RTO;NRO - surface";
            }
            header+=Lineaires.header(parametres, ";T - ", detail);
        }
        if (distri){
            if (detail == 2)
                header+=";Nb PM ext;Nb PM int";
            if (detail > 0) header+=";PM ext - Armoires de rue 450;PM ext - Armoires de rue 150;PM ext - Coupleurs 1:32;PM ext - Coupleurs 1:64;PM ext - tiroirs coupleurs;"
                    + "PM int - Armoires interieures;PM int - Coupleurs 1:4;PM int - Coupleurs 1:8;PM int - Coupleurs 1:16;PM int - Tiroirs coupleurs";
            else header += ";Nb PM";
            header+=Lineaires.header(parametres, ";D - ", detail);
            if (detail > 0){
                header += ";PBO aeriens;PBO souterrains";
                if (transport)
                    header+= ";PBO int;Vertical - adduction immeubles;Vertical - cablage logements";
            }else 
                header += ";Nb PBO";
        }
        return header;
    }

    public String print(Parametres parametres, int detail){
        String suite = String.valueOf(nbLignesExt+this.nbLignesInt);
        if (isTransport){
            if (isDistri){
                if (detail > 0)
                    suite += ";"+nbLignesExt+";"+this.nbLignesInt;
                suite+=";"+this.nbNRO;
                if (detail > 0)
                    suite += ";"+this.nbTiroirsOptiques+";"+this.nbCoupleurs[0]+";"+this.nbTiroirsCoupleurs[0]+";"+this.nbRTO+";"+String.valueOf(this.surfaceNRO).replace(".", ",");
            }else {
                switch(detail){ // absence volontaire de breaks 
                    case 2:
                        suite+=";;";
                    case 1:
                        suite+=";;";
                    default:
                        suite+=";";
                }
            }
            suite += this.linNROPM.print(parametres, detail);
        }
        if (isDistri){
            if (detail == 2)
                suite += ";"+this.nbPMext+";"+this.nbPMint;
            if (detail > 0)
                suite+= ";"+(this.nbPM_ZMD+this.nbPM_ZTD_BD)+";"+this.nbPM_ZTD_HD+";"
                        +this.nbCoupleurs[4]+";"+this.nbCoupleurs[5]+";"+this.nbTiroirsCoupleurs[2]+";"
                        +this.nbArmoiresPMint+";"+this.nbCoupleurs[1]+";"+this.nbCoupleurs[2]+";"+this.nbCoupleurs[3]+";"+this.nbTiroirsCoupleurs[1];
            else suite += ";"+(this.nbPMext+this.nbPMint);
            suite += this.linPMPBO.print(parametres, detail);
            if (detail > 0){
                suite += ";"+(nbPBO[0]+nbPBO[1])+";"+(nbPBO[2]+nbPBO[3]);
                if (isTransport)
                    suite += ";"+this.nbPBOimmeubles+";"+this.nbImmeubles+";"+this.nbLogementsImmeubles;
            }
            else{
                suite += ";"+(nbPBO[0]+nbPBO[1]+nbPBO[2]+nbPBO[3]+this.nbPBOimmeubles);
            }
        }
        return suite;
    }

    public String printIncomingCables(boolean distri){
        if (distri) return linPMPBO.printIncomingCables();
        else return linNROPM.printIncomingCables();
    }

    public Couts calculCouts(CoutsUnitaires couts, Parametres parametres){
        double[] coutsLinD = this.linPMPBO.coutsLineaires(couts, parametres);
        double[] coutsLinT = this.linNROPM.coutsLineaires(couts, parametres);
        double coutPBOaerien = (nbPBO[0]+nbPBO[1])*couts.CoutUnitairePBO_ext6;
        double coutPBOsouterrain = (nbPBO[2]+nbPBO[3])*couts.CoutUnitairePBO_ext6;
        double coutVerticalPBO = this.nbPBOimmeubles*couts.PBO_int6;
        double coutVerticalCables = this.nbImmeubles*couts.adduction + this.nbLogementsImmeubles*couts.colonneMontante;
        double coutPM_int = this.nbPM_ZTD_HD*couts.CoutUnitairePM_int;
        double coutPM_ext = (this.nbPM_ZMD+this.nbPM_ZTD_BD)*couts.CoutUnitairePM_ext300+this.nbPM_ZTD_HD*couts.CoutUnitairePM_ext100;;
        double coutsNRO = nbTiroirsOptiques*couts.coutTiroirOptique + nbRTO*couts.coutRTO + surfaceNRO*couts.coutSurfacique;
        
        return new Couts(nbLignesExt+nbLignesInt, coutsLinD[0]+coutsLinT[0], coutsLinD[1]+coutsLinT[1], coutsLinD[2]+coutsLinT[2],
                coutsLinD[3]+coutsLinT[3], coutPBOaerien, coutPBOsouterrain, coutsLinD[4]+coutsLinT[4], coutsLinD[5]+coutsLinT[5],
                coutVerticalPBO, coutVerticalCables, coutPM_int, coutPM_ext, coutsNRO, coutsLinD[6]+coutsLinT[6]);
    }
    
}
