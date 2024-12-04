/*
 * Copyright (c) 2024, Autorité de régulation des communications électroniques, des postes et de la distribution de la presse
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

import java.util.List;
import java.util.Random;

public class Noeud extends NoeudAGarder {
        
    // numéro en tant que PM:
    int numPM;
    
    // numéro en tant que PMint
    boolean isPMint;
    
    // champs techniques pour l'algorithme de placement des PM ext avec la méthode de la "mediane"
    int lignesInfSeuil;
    int lignesSupSeuil;
    List<Double> distri;
    
    // champs techniques pour l'algorithme de placement des PM ext avec la méthode de la "moyenne"
    double longueurMoyenne;
    
    // taille PM en tant que PM ext 
    private int taillePM_ZMD = 0;
    private int taillePM_ZTD_BD = 0;
    private int taillePM_ZTD_HD = 0;

    public Noeud(String[] fields, int seuilPMint){
        this.id = Integer.parseInt(fields[0]);
        this.coord = new double[]{Double.parseDouble(fields[1]), Double.parseDouble(fields[2])};
        this.init();

        int placeIsPMint = 4+2*Integer.parseInt(fields[3]);
        for (int i = 4;i<placeIsPMint;i+=2){
            String zoneLue = fields[i];
            String zone = zoneLue.replace("_AMII","").replace("_RIP", "");
            int nbLignes = Integer.parseInt(fields[i+1]);
            this.addDemande(zone, nbLignes);
        }
        if (fields[placeIsPMint].equals("1") || this.getDemandeLocale("ZTD_HD") >= seuilPMint){
            this.isPMint = true;
        } else this.isPMint = false;
        if (this.hasDemandeLocale()){
            for (int i = placeIsPMint+2;i<fields.length;i++){
                this.addToPath(Long.parseLong(fields[i]));
            }
        }
    }

    public boolean decreaseDemande(String zone, Random PRNG){ // fonction permettant de diminuer la demande au niveau d'une zone 
        if (zone.equals("ZTD")){
            int i = PRNG.nextInt(this.demandeLocaleTotale()) + 1; // renvoie un entier avec un tirage uniformément distribué entre 1 et demandeLocaleTotale
            if (i<=this.getDemandeLocale("ZTD_BD")){
                this.modifieDemande("ZTD_BD", -1);
            } else {
                this.modifieDemande("ZTD_HD", -1);
            }
        }
        else{
            this.modifieDemande("ZMD", -1);
        }
        return this.demandeLocaleTotale() == 0;
    }

    public void increaseDemande(String zone, int seuilPMint, Random PRNG){ // fonction permettant d'augmenter la demande au niveau d'une zone 
        if (zone.equals("ZTD")){
            //int i = (int) Math.ceil(Math.random()*demandeLocaleTotale());
            int i = PRNG.nextInt(this.demandeLocaleTotale()) + 1; // pour être conforme au "ceil" avant
            if (i<=this.getDemandeLocale("ZTD_BD")){
                this.modifieDemande("ZTD_BD", 1);
            } else {
                this.modifieDemande("ZTD_HD", 1);
            } 
            if (!this.isPMint && this.getDemandeLocale("ZTD_HD") >= seuilPMint)
                this.isPMint = true;
        }
        else{
            this.modifieDemande("ZMD", 1);
        }
    }

    public int demandeLocaleExt(String zone){
        switch(zone){
            case "ZTD_HD":
                if (this.isPMint) return 0;
            default:
                return this.getDemandeLocale(zone);
        }
    }

    public boolean isPMint(){
        return this.demandeLocaleInt() > 0;
    }

    public int demandeLocaleInt(){
        if (this.isPMint) return this.getDemandeLocale("ZTD_HD");
        else return 0;
    }

    // méthodes liées au PM
    
    public boolean isPMExt(String zone) {
        return taillePM(zone) > 0;
    }

    public int taillePM(String zone) {
        switch (zone) {
            case "ZMD":
                return taillePM_ZMD;
            case "ZTD_BD":
                return taillePM_ZTD_BD;
            default:
                return taillePM_ZTD_HD;
        }
    }

    public void setTaillePM(String zone, int n) {
        switch (zone) {
            case "ZMD":
                taillePM_ZMD = n;
                break;
            case "ZTD_BD":
                taillePM_ZTD_BD = n;
                break;
            case "ZTD_HD":
                taillePM_ZTD_HD = n;
                break;
        }
    }
    
}
