import React, { useEffect, useState } from 'react';
import { Typography, Box, Paper, Stack, Button, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Alert } from '@mui/material';
import { Download as DownloadIcon } from '@mui/icons-material';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import useClientId from '../hooks/useClientId';
import AssignmentMap from '../Map';

/**
 * Results page that displays the optimal student-teacher assignments.
 *
 * Connects to the scheduler service's STOMP WebSocket endpoint to receive
 * assignment data in real time, with a REST fallback for page refreshes.
 * Assignments are displayed both on an interactive Google Map and in a table.
 *
 * The administrator can review assignments and download them as a CSV.
 */
export default function Results() {
  const [assignments, setAssignments] = useState([]);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState(null);
  const { clientId, loading } = useClientId();

  // REST fallback: fetch existing assignments on mount
  useEffect(() => {
    if (loading || !clientId) return;

    fetch('/api/assignments', { credentials: 'include' })
      .then((res) => {
        if (res.ok) return res.json();
        return [];
      })
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          setAssignments(data);
        }
      })
      .catch((err) => console.error('Failed to fetch existing assignments:', err));
  }, [clientId, loading]);

  // STOMP WebSocket connection for real-time updates
  useEffect(() => {
    if (loading || !clientId) return;

    const stompClient = new Client({
      webSocketFactory: () => new SockJS('/assignments'),
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        setError(null);

        stompClient.subscribe(`/topic/${clientId}/upload/assignments`, (message) => {
          try {
            const data = JSON.parse(message.body);
            if (Array.isArray(data)) {
              setAssignments(data);
            }
          } catch (e) {
            console.error('Failed to parse assignment data:', e);
          }
        });
      },
      onStompError: (frame) => {
        setError('WebSocket error: ' + (frame.headers?.message || 'unknown'));
        setConnected(false);
      },
      onWebSocketClose: () => {
        setConnected(false);
      },
    });

    stompClient.activate();

    return () => {
      stompClient.deactivate();
    };
  }, [clientId, loading]);

  /**
   * Exports the assignments as a CSV file and triggers a browser download.
   */
  function downloadCSV() {
    if (assignments.length === 0) return;

    const headers = ['Student ID', 'Student Name', 'Student Email', 'Teacher ID', 'Teacher Name', 'Teacher Email', 'Teacher Address'];
    const rows = assignments.map((a) => [
      a.student?.id || '',
      `${a.student?.firstName || ''} ${a.student?.lastName || ''}`,
      a.student?.email || '',
      a.teacher?.id || '',
      `${a.teacher?.firstName || ''} ${a.teacher?.lastName || ''}`,
      a.teacher?.email || '',
      a.teacher?.address || '',
    ]);

    const csvContent = [headers, ...rows]
      .map((row) => row.map((cell) => `"${cell}"`).join(','))
      .join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'assignments.csv';
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <Box sx={{ maxWidth: 1200, mx: 'auto', mt: 2 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
        <Typography variant="h4" color="#007c41">
          Assignment Results
        </Typography>
        <Button
          variant="contained"
          startIcon={<DownloadIcon />}
          onClick={downloadCSV}
          disabled={assignments.length === 0}
          sx={{ backgroundColor: '#007c41', '&:hover': { backgroundColor: '#005a2e' } }}
        >
          Download CSV
        </Button>
      </Stack>

      {error && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {error} — Assignments will load via REST fallback.
        </Alert>
      )}

      {assignments.length === 0 ? (
        <Paper elevation={2} sx={{ p: 4, textAlign: 'center' }}>
          <Typography variant="body1" color="text.secondary">
            {connected
              ? 'Waiting for assignments from the scheduler...'
              : 'Connecting to the scheduler service...'}
          </Typography>
        </Paper>
      ) : (
        <Stack spacing={3}>
          {/* Map view */}
          <Paper elevation={2} sx={{ p: 2, height: 500 }}>
            <AssignmentMap assignments={assignments} />
          </Paper>

          {/* Table view */}
          <TableContainer component={Paper} elevation={2}>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ backgroundColor: '#007c41' }}>
                  <TableCell sx={{ color: '#f2cd00', fontWeight: 'bold' }}>Student</TableCell>
                  <TableCell sx={{ color: '#f2cd00', fontWeight: 'bold' }}>Student Email</TableCell>
                  <TableCell sx={{ color: '#f2cd00', fontWeight: 'bold' }}>Teacher</TableCell>
                  <TableCell sx={{ color: '#f2cd00', fontWeight: 'bold' }}>Teacher Email</TableCell>
                  <TableCell sx={{ color: '#f2cd00', fontWeight: 'bold' }}>Location</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {assignments.map((a, idx) => (
                  <TableRow key={idx} sx={{ '&:nth-of-type(odd)': { backgroundColor: '#f5f5f5' } }}>
                    <TableCell>{a.student?.firstName} {a.student?.lastName}</TableCell>
                    <TableCell>{a.student?.email}</TableCell>
                    <TableCell>{a.teacher?.firstName} {a.teacher?.lastName}</TableCell>
                    <TableCell>{a.teacher?.email}</TableCell>
                    <TableCell>{a.teacher?.address}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>

          <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center' }}>
            {assignments.length} total assignments
          </Typography>
        </Stack>
      )}
    </Box>
  );
}
