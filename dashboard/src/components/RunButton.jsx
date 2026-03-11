import { useState, useEffect } from 'react';
import { Box, Button } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useUpload } from './UploadContext';

/**
 * Button that triggers the scheduling run.
 *
 * Disabled until both student and teacher data have been validated
 * (studentValStatus === 'complete' && teacherValStatus === 'complete').
 * Navigates to the /running page on click.
 */
export default function RunScheduler({ onRun }) {
  const [isDisabled, setIsDisabled] = useState(true);
  const { teacherValStatus, studentValStatus } = useUpload();
  const navigate = useNavigate();

  const bothComplete = studentValStatus === 'complete' && teacherValStatus === 'complete';

  useEffect(() => {
    setIsDisabled(!bothComplete);
  }, [bothComplete]);

  const handleClick = () => {
    if (onRun) onRun();
    navigate('/running');
  };

  return (
    <Box>
      <Button
        variant="contained"
        disabled={isDisabled}
        onClick={handleClick}
        sx={{
          height: 75,
          width: '90%',
          fontWeight: 'bold',
          fontSize: '20px',
          color: bothComplete ? '#f2cd00' : undefined,
          backgroundColor: bothComplete ? '#007c41' : undefined,
          '&:hover': {
            backgroundColor: bothComplete ? '#005a2e' : undefined,
          },
        }}
      >
        Run Scheduler
      </Button>
    </Box>
  );
}
