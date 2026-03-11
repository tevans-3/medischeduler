import React from 'react';
import { APIProvider, Map, Marker, InfoWindow } from '@vis.gl/react-google-maps';
import { useState } from 'react';

/**
 * Google Maps component for displaying student-teacher assignment results.
 *
 * Renders an embedded map centered on Edmonton, AB with markers for
 * each assignment. Clicking a marker shows an info window with the
 * student and teacher names.
 *
 * Props:
 *   assignments - array of { student, teacher } objects with address/lat/lng
 */
export default function AssignmentMap({ assignments = [] }) {
  const [selectedAssignment, setSelectedAssignment] = useState(null);

  // Default center: Edmonton, AB
  const defaultCenter = { lat: 53.5461, lng: -113.4937 };

  return (
    <APIProvider apiKey={import.meta.env.VITE_GOOGLE_MAPS_API_KEY || ''}>
      <div style={{ width: '100%', height: '100%', minHeight: '500px' }}>
        <Map
          defaultZoom={10}
          defaultCenter={defaultCenter}
          gestureHandling="greedy"
          disableDefaultUI={false}
          mapTypeId="roadmap"
        >
          {assignments.map((assignment, index) => {
            const position = assignment.lat && assignment.lng
              ? { lat: assignment.lat, lng: assignment.lng }
              : defaultCenter;

            return (
              <Marker
                key={index}
                position={position}
                title={`${assignment.student?.firstName} ${assignment.student?.lastName}`}
                onClick={() => setSelectedAssignment(assignment)}
              />
            );
          })}

          {selectedAssignment && (
            <InfoWindow
              position={
                selectedAssignment.lat && selectedAssignment.lng
                  ? { lat: selectedAssignment.lat, lng: selectedAssignment.lng }
                  : defaultCenter
              }
              onCloseClick={() => setSelectedAssignment(null)}
            >
              <div>
                <h3>Assignment</h3>
                <p>
                  <strong>Student:</strong>{' '}
                  {selectedAssignment.student?.firstName}{' '}
                  {selectedAssignment.student?.lastName}
                </p>
                <p>
                  <strong>Teacher:</strong>{' '}
                  {selectedAssignment.teacher?.firstName}{' '}
                  {selectedAssignment.teacher?.lastName}
                </p>
                <p>
                  <strong>Address:</strong>{' '}
                  {selectedAssignment.teacher?.address}
                </p>
              </div>
            </InfoWindow>
          )}
        </Map>
      </div>
    </APIProvider>
  );
}
