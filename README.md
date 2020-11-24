# OBD2WIFIConnector
Progetto di "Terminali Mobili e Multimedialità" - Corso Di Laurea Triennale in Informatica

## Descrizione e funzionamento
L'applicazione ottiene dati diagnostici in tempo reale dalle ECU di auto e moto grazie al supporto di un'interfaccia ELM327-OBD2 collegata in rete.
L'obiettivo è fornire una GUI che disponga delle seguenti funzionalità :


Indicatori : Stato veicolo, Temperatura aria interna/esterna, Livello e pressione carburante, Voltaggio batteria

Grafici Real Time : Velocità, Giri Motore, Temperatura olio, Temperatura refrigerante

Elenchi : Codici di errore temporanei e permanenti presenti in centralina (spia gialla) 

## Requisiti
Per questo progetto è necessario disporre di :

1) Interfaccia Wifi ELM327-OBD2 

2) Eventuale adattatore per interfaccia a 3 poli di tipo FIAT (presente su auto e moto prodotte ​nel periodo 1998-2010)

3) Una ECU compatibile OBD2 ottenuta da :

    a) Veicolo da sacrificare

    b) ECU Simulator (soluzione performante ma decisamente costosa)

    c) Arduino Uno + CAN-BUS Shield

  
  
  
## Riferimenti
Kotlin OBD API : 
https://github.com/eltonvs/kotlin-obd-api

Arduino ECU Simulator: 
https://www.instructables.com/Arduino-OBD2-Simulator/

https://www.youtube.com/watch?v=6bu9Dwti1tU

