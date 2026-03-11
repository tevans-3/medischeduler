import React, { useEffect, useState, useRef } from 'react';
import { Typography, Box, Stack, Paper } from '@mui/material';
import LinearProgress from '@mui/material/LinearProgress';
import { useUpload } from '../components/UploadContext';
import { useNavigate } from 'react-router-dom';

/**
 * Page displayed while the scheduler is running.
 *
 * On mount, sends the uploaded student and teacher data to the backend
 * POST /matches endpoint. Then polls GET /running to track progress
 * (total, processed, failed, notFound) and displays a progress bar.
 *
 * When all routes have been processed, navigates to the /results page.
 */
export default function Running() {
  const { studentUploadData, teacherUploadData } = useUpload();
  const navigate = useNavigate();

  const [totalCount, setTotalCount] = useState(0);
  const [processedCount, setProcessedCount] = useState(0);
  const [failedCount, setFailedCount] = useState(0);
  const [notFoundCount, setNotFoundCount] = useState(0);
  const [status, setStatus] = useState('uploading');
  const pollingRef = useRef(null);

  // Step 1: Submit the data to the backend
  useEffect(() => {
    if (!studentUploadData || !teacherUploadData) {
      setStatus('error');
      return;
    }

    document.cookie = "clientId="+crypto.randomUUID(); 

    const payload = {
      students: studentUploadData,
      teachers: teacherUploadData,
    };

    fetch('/matches', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include',
      body: JSON.stringify(payload),
    })
      .then((response) => {
        if (!response.ok) throw new Error('Upload failed');
        return response.text();
      })
      .then(() => {
        setStatus('processing');
      })
      .catch((error) => {
        console.error('Error submitting data:', error);
        setStatus('error');
      });
  }, []);

  // Step 2: Poll for progress while processing
  useEffect(() => {
    if (status !== 'processing') return;

    pollingRef.current = setInterval(() => {
      fetch('/running', {
        method: 'GET',
        credentials: 'include',
      })
        .then((response) => response.json())
        .then((data) => {
          setTotalCount(data.totalCount || 0);
          setProcessedCount(data.processedSoFar || 0);
          setFailedCount(data.failedSoFar || 0);
          setNotFoundCount(data.notFoundSoFar || 0);

          const total = data.totalCount || 0;
          const done = (data.processedSoFar || 0) + (data.failedSoFar || 0) + (data.notFoundSoFar || 0);

          if (total > 0 && done >= total) {
            setStatus('complete');
            clearInterval(pollingRef.current);
            // Navigate to results after a short delay
            setTimeout(() => navigate('/results'), 1500);
          }
        })
        .catch((error) => {
          console.error('Error polling progress:', error);
        });
    }, 3000);

    return () => clearInterval(pollingRef.current);
  }, [status, navigate]);

  const totalDone = processedCount + failedCount + notFoundCount;
  const progressPct = totalCount > 0 ? (totalDone / totalCount) * 100 : 0;

  return (
    <Box sx={{ maxWidth: 800, mx: 'auto', mt: 4 }}>
      <Typography variant="h4" color="#007c41" gutterBottom>
        Scheduler Running
      </Typography>

      {status === 'uploading' && (
        <Typography variant="body1" sx={{ mb: 2 }}>
          Submitting student and teacher data to the server...
        </Typography>
      )}

      {status === 'error' && (
        <Paper elevation={2} sx={{ p: 3, backgroundColor: '#fff3f3' }}>
          <Typography variant="body1" color="error">
            An error occurred. Please go back and ensure both CSV files are uploaded correctly.
          </Typography>
        </Paper>
      )}

      {(status === 'processing' || status === 'complete') && (
        <Stack spacing={3}>
          <Paper elevation={2} sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Computing Route Matrix
            </Typography>
            <LinearProgress
              variant="determinate"
              value={progressPct}
              sx={{
                height: 20,
                borderRadius: 2,
                '& .MuiLinearProgress-bar': { backgroundColor: '#007c41' },
              }}
            />
            <Typography variant="body2" sx={{ mt: 1 }}>
              {Math.round(progressPct)}% complete ({totalDone} / {totalCount} routes)
            </Typography>
          </Paper>

          <Stack direction="row" spacing={2}>
            <Paper elevation={1} sx={{ p: 2, flex: 1, textAlign: 'center' }}>
              <Typography variant="h5" color="#007c41">{processedCount}</Typography>
              <Typography variant="caption">Processed</Typography>
            </Paper>
            <Paper elevation={1} sx={{ p: 2, flex: 1, textAlign: 'center' }}>
              <Typography variant="h5" color="warning.main">{notFoundCount}</Typography>
              <Typography variant="caption">No Route Found</Typography>
            </Paper>
            <Paper elevation={1} sx={{ p: 2, flex: 1, textAlign: 'center' }}>
              <Typography variant="h5" color="error">{failedCount}</Typography>
              <Typography variant="caption">Failed</Typography>
            </Paper>
          </Stack>

          {status === 'complete' && (
            <Typography variant="body1" color="#007c41" sx={{ fontWeight: 'bold' }}>
              All routes computed. Generating optimal assignments...
            </Typography>
          )}
        </Stack>
      )}
    </Box>
  );
}
