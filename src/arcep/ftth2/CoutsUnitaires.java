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

import java.io.*;
import javax.swing.*;

public class CoutsUnitaires {

    double[] CoutUnitaireCablesAeriens = new double[8];
    double[] CoutUnitaireCablesSouterrains = new double[10];
    double[] CoutUnitaireBoitiersAeriens = new double[8];
    double[] CoutUnitaireBoitiersSouterrains = new double[10];
   
    double adduction;
    double colonneMontante;
    double PBO_int6;

    double CoutUnitairePBO_ext6;

    double CoutUnitaireConduite;
    double CoutUnitaireConduiteAllegee;
    double CoutUnitairePoteau;

    double CoutUnitairePM_int;
    double CoutUnitairePM_ext300;
    double CoutUnitairePM_ext100;

    
    //paramètres de coût NRO
    double coutSurfacique; //€/m^2
    double coutTiroirOptique; 
    double coutRTO; 
    
    double CoutUnitaireCoupleur2;
    double CoutUnitaireCoupleur4;
    double CoutUnitaireCoupleur8;
    double CoutUnitaireCoupleur16;
    double CoutUnitaireCoupleur32;
    double CoutUnitaireCoupleur64;

    double etudesAerien;
    double etudesSouterrain;
        
    private void initCout(String id, double valeur){
        switch (id) {
            case "CS006":
                CoutUnitaireCablesSouterrains[0] = valeur;
                break;
            case "CS012":
                CoutUnitaireCablesSouterrains[1] = valeur;
                break;
            case "CS024":
                CoutUnitaireCablesSouterrains[2] = valeur;
                break;
            case "CS048":
                CoutUnitaireCablesSouterrains[3] = valeur;
                break;
            case "CS072":
                CoutUnitaireCablesSouterrains[4] = valeur;
                break;
            case "CS096":
                CoutUnitaireCablesSouterrains[5] = valeur;
                break;
            case "CS144":
                CoutUnitaireCablesSouterrains[6] = valeur;
                break;
            case "CS288":
                CoutUnitaireCablesSouterrains[7] = valeur;
                break;
            case "CS576":
                CoutUnitaireCablesSouterrains[8] = valeur;
                break;
            case "CS720":
                CoutUnitaireCablesSouterrains[9] = valeur;
                break;
            case "CA006":
                CoutUnitaireCablesAeriens[0] = valeur;
                break;
            case "CA012":
                CoutUnitaireCablesAeriens[1] = valeur;
                break;
            case "CA024":
                CoutUnitaireCablesAeriens[2] = valeur;
                break;
            case "CA048":
                CoutUnitaireCablesAeriens[3] = valeur;
                break;
            case "CA072":
                CoutUnitaireCablesAeriens[4] = valeur;
                break;
            case "CA096":
                CoutUnitaireCablesAeriens[5] = valeur;
                break;
            case "CA144":
                CoutUnitaireCablesAeriens[6] = valeur;
                break;
            case "CA288":
                CoutUnitaireCablesAeriens[7] = valeur;
                break;
            case "BS006":
                CoutUnitaireBoitiersSouterrains[0] = valeur;
                break;
            case "BS012":
                CoutUnitaireBoitiersSouterrains[1] = valeur;
                break;
            case "BS024":
                CoutUnitaireBoitiersSouterrains[2] = valeur;
                break;
            case "BS048":
                CoutUnitaireBoitiersSouterrains[3] = valeur;
                break;
            case "BS072":
                CoutUnitaireBoitiersSouterrains[4] = valeur;
                break;
            case "BS096":
                CoutUnitaireBoitiersSouterrains[5] = valeur;
                break;
            case "BS144":
                CoutUnitaireBoitiersSouterrains[6] = valeur;
                break;
            case "BS288":
                CoutUnitaireBoitiersSouterrains[7] = valeur;
                break;
            case "BS576":
                CoutUnitaireBoitiersSouterrains[8] = valeur;
                break;
            case "BS720":
                CoutUnitaireBoitiersSouterrains[9] = valeur;
                break;
            case "BA006":
                CoutUnitaireBoitiersAeriens[0] = valeur;
                break;
            case "BA012":
                CoutUnitaireBoitiersAeriens[1] = valeur;
                break;
            case "BA024":
                CoutUnitaireBoitiersAeriens[2] = valeur;
                break;
            case "BA048":
                CoutUnitaireBoitiersAeriens[3] = valeur;
                break;
            case "BA072":
                CoutUnitaireBoitiersAeriens[4] = valeur;
                break;
            case "BA096":
                CoutUnitaireBoitiersAeriens[5] = valeur;
                break;
            case "BA144":
                CoutUnitaireBoitiersAeriens[6] = valeur;
                break;
            case "BA288":
                CoutUnitaireBoitiersAeriens[7] = valeur;
                break;
            case "Adduction":
                adduction = valeur;
                break;
            case "Colonne":
                colonneMontante = valeur;
                break;
            case "PBOInt":
                PBO_int6 = valeur;
                break;
            case "PBOExt":
                CoutUnitairePBO_ext6 = valeur;
                break;
            case "ConduitePleine":
                CoutUnitaireConduite = valeur;
                break;
            case "ConduiteAllegee":
                CoutUnitaireConduiteAllegee = valeur;
                break;
            case "Poteaux":
                CoutUnitairePoteau = valeur;
                break;
            case "PMInt":
                CoutUnitairePM_int = valeur;
                break;
            case "PMExt300":
                CoutUnitairePM_ext300 = valeur;
                break;
            case "PMExt100":
                CoutUnitairePM_ext100 = valeur;
                break;
            case "Coupleur02":
                CoutUnitaireCoupleur2 = valeur;
                break;
            case "Coupleur04":
                CoutUnitaireCoupleur4 = valeur;
                break;
            case "Coupleur08":
                CoutUnitaireCoupleur8 = valeur;
                break;
            case "Coupleur16":
                CoutUnitaireCoupleur16 = valeur;
                break;
            case "Coupleur32":
                CoutUnitaireCoupleur32 = valeur;
                break;
            case "Coupleur64":
                CoutUnitaireCoupleur64 = valeur;
                break;
            case "EtudesAerien":
                etudesAerien = valeur;
                break;
            case "EtudesSout":
                etudesSouterrain = valeur;
                break;
            case "Shelter":
                coutSurfacique = valeur;
                break;
            case "TiroirOpt":
                coutTiroirOptique = valeur;
                break;
            case "CoutRTO":
                coutRTO = valeur;
                break;           
        }
    }
    
    public void initCouts(String fichier) {
        try {
            BufferedReader ficCU = new BufferedReader(new FileReader(fichier));
            String ligne, donneesLigne[];
            ficCU.readLine(); // en-tête
            while ((ligne = ficCU.readLine()) != null) {
                donneesLigne = ligne.split(";");
                String id = donneesLigne[1];
                double valeur = Double.parseDouble(donneesLigne[4].replace(",", "."));
                initCout(id, valeur);
            }
            ficCU.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void initCouts(JTable tableauCU) {
        for (int i = 0; i < tableauCU.getRowCount(); i++) {
            String id = (String) tableauCU.getValueAt(i, 1);
            double valeur = Double.parseDouble(((String) tableauCU.getValueAt(i, 4)).replace(",", ".").replace(" ", ""));
            initCout(id, valeur);
        }
    }
    
}
