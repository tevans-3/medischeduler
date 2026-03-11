import React from 'react';
import { Modal, Box, Typography, Stack, IconButton } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import StudentValProgressBar from './StudentValProgressBar';
import TeacherValProgressBar from './TeacherValProgressBar';
import RunScheduler from './RunButton';
import { useUpload } from './UploadContext';

const modalStyle = {
  position: 'absolute',
  top: '50%',
  left: '50%',
  transform: 'translate(-50%, -50%)',
  width: '80%',
  maxWidth: 800,
  bgcolor: 'background.paper',
  borderRadius: 2,
  boxShadow: 24,
  p: 4,
};

export default function ValidationModal({ open, onClose }) {
  const {
    studentValStatus,
    teacherValStatus,
    studentUploadStatus,
    teacherUploadStatus,
  } = useUpload();

  const showStudentBar = studentUploadStatus === 'complete' && teacherUploadStatus === 'complete';
  const showTeacherBar = studentValStatus === 'complete' && teacherUploadStatus === 'complete';
  const showRunButton = teacherValStatus === 'complete';

  return (
    <Modal
      open={open}
      onClose={onClose}
      aria-labelledby="validation-modal-title"
    >
      <Box sx={modalStyle}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
          <Typography id="validation-modal-title" variant="h5" color="#007c41" fontWeight="bold">
            Data Validation
          </Typography>
          <IconButton onClick={onClose} size="small">
            <CloseIcon />
          </IconButton>
        </Box>

        <Stack spacing={3} alignItems="center">
          {showStudentBar && <StudentValProgressBar />}
          {showTeacherBar && <TeacherValProgressBar />}
          {showRunButton && <RunScheduler onRun={onClose} />}
        </Stack>
      </Box>
    </Modal>
  );
}
