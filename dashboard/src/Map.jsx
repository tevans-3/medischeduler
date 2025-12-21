import React from 'react'; 
import {createRoote} from 'react-dom/client'; 
import {APIProvider, Map} from '@vis.gl/react-google-maps'; 

window.initMap = function() {
    const position = { lat: 53.5461, lng: 113.4937}; 

    const map = new google.maps.Map(document.getElementById('map'), {
        zoom: 8, 
        center: centerLocation 
    })

    const market = new google.maps.Marker({ 
        position: centerLocation, 
        map,
        title: 'Results Display'
    });
}

        <APIProvider apiKey={import.meta.env.VITE_API_KEY}>
          <div style={{width: '100%', height:'100%'}}>
          <Map
            defaultZoom={3}
            defaultCenter={{ lat: 53.5461, lng: 113.4937}}
            gestureHandling={'greedy'}
            disableDefaultUI={false}
            mapTypeId="roadmap"
          ></Map>
          </div>